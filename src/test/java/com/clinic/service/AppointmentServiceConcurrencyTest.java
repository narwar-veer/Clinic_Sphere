package com.clinic.service;

import com.clinic.dto.request.AppointmentBookingRequest;
import com.clinic.dto.response.AppointmentResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Gender;
import com.clinic.entity.Patient;
import com.clinic.entity.Slot;
import com.clinic.exception.ConflictException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.SlotRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

class AppointmentServiceConcurrencyTest {

    @Test
    void concurrentBooking_onlyOneSucceeds_otherGetsConflict() throws Exception {
        SlotRepository slotRepository = Mockito.mock(SlotRepository.class);
        PatientRepository patientRepository = Mockito.mock(PatientRepository.class);
        AppointmentRepository appointmentRepository = Mockito.mock(AppointmentRepository.class);
        AppointmentMapper appointmentMapper = Mockito.mock(AppointmentMapper.class);
        PageResponseFactory pageResponseFactory = Mockito.mock(PageResponseFactory.class);
        BookingWindowValidator bookingWindowValidator = Mockito.mock(BookingWindowValidator.class);
        PaginationValidator paginationValidator = Mockito.mock(PaginationValidator.class);
        OutboxService outboxService = Mockito.mock(OutboxService.class);
        AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
        AppTime appTime = Mockito.mock(AppTime.class);

        AppointmentService appointmentService = new AppointmentService(
                slotRepository,
                patientRepository,
                appointmentRepository,
                appointmentMapper,
                pageResponseFactory,
                bookingWindowValidator,
                paginationValidator,
                outboxService,
                auditLogService,
                appTime,
                new SimpleMeterRegistry());

        Slot slot = new Slot();
        slot.setId(11L);
        slot.setSlotDate(LocalDate.of(2026, 5, 28));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setMaxPatients(2);
        slot.setBookedCount(0);
        slot.setIsBlocked(false);

        Patient patient = new Patient();
        patient.setId(5L);
        patient.setPhone("+919999999999");
        patient.setName("John");
        patient.setAge(30);
        patient.setGender(Gender.MALE);

        Mockito.when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(slot));
        Mockito.when(patientRepository.findByIdentityForUpdate("+919999999999", "John", 30, Gender.MALE))
                .thenReturn(Optional.of(patient));
        Mockito.when(appointmentRepository.findFirstByPatientIdAndSlotSlotDateAndStatusNotOrderBySlotStartTimeAsc(
                        5L,
                        LocalDate.of(2026, 5, 28),
                        AppointmentStatus.CANCELLED))
                .thenReturn(Optional.empty());

        AtomicInteger saveCounter = new AtomicInteger();
        Mockito.when(appointmentRepository.saveAndFlush(Mockito.any(Appointment.class))).thenAnswer(invocation -> {
            int count = saveCounter.incrementAndGet();
            if (count == 1) {
                Appointment appointment = invocation.getArgument(0);
                appointment.setId(100L);
                return appointment;
            }
            throw new DataIntegrityViolationException("duplicate active booking");
        });

        Mockito.when(slotRepository.save(Mockito.any(Slot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(outboxService.enqueue(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn("event-1");
        Mockito.when(appointmentMapper.toResponse(Mockito.any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            AppointmentResponse response = new AppointmentResponse();
            response.setId(appointment.getId());
            return response;
        });

        AppointmentBookingRequest request = new AppointmentBookingRequest();
        request.setSlotId(11L);
        request.setName("John");
        request.setPhone("+919999999999");
        request.setAge(30);
        request.setGender(Gender.MALE);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Object> task1 = () -> {
            start.await();
            return appointmentService.bookAppointment(request, "idem-1");
        };
        Callable<Object> task2 = () -> {
            start.await();
            return appointmentService.bookAppointment(request, "idem-2");
        };

        Future<Object> first = executor.submit(task1);
        Future<Object> second = executor.submit(task2);
        start.countDown();

        int success = 0;
        int conflict = 0;
        for (Future<Object> future : new Future[]{first, second}) {
            try {
                Object result = future.get();
                if (result instanceof AppointmentResponse) {
                    success++;
                }
            } catch (Exception ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                if (cause instanceof ConflictException) {
                    conflict++;
                } else {
                    throw ex;
                }
            }
        }

        executor.shutdownNow();

        Assertions.assertEquals(1, success);
        Assertions.assertEquals(1, conflict);
        Mockito.verify(outboxService, Mockito.times(1))
                .enqueue(Mockito.eq("APPOINTMENT"), Mockito.anyString(), Mockito.eq("APPOINTMENT_CONFIRMATION"), Mockito.any());
    }
}
