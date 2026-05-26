package com.clinic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "clinics")
public class Clinic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "clinic_name", nullable = false, length = 150)
    private String clinicName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String whatsapp;

    @Column(length = 150)
    private String email;

    @Column(name = "map_location", columnDefinition = "TEXT")
    private String mapLocation;

    @OneToMany(mappedBy = "clinic")
    private List<ClinicTiming> timings = new ArrayList<>();

    @OneToOne(mappedBy = "clinic")
    private SlotConfig slotConfig;
}
