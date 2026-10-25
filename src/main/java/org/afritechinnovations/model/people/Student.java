package org.afritechinnovations.model.people;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;

import java.time.LocalDate;

@Entity
@Table(name = "students",
        uniqueConstraints = @UniqueConstraint(columnNames = {"school_id", "registration_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Historical V60 snapshot only. The operational personal email is obtained from user. */
    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Snapshot only when constructing an older-style record; persisted records have their own names. */
    @PrePersist
    public void initializeSchoolIdentity() {
        boolean unsnapshotted = firstName == null && lastName == null;
        if (firstName == null && user != null) firstName = user.getFirstName();
        if (lastName == null && user != null) lastName = user.getLastName();
        if (unsnapshotted && user != null) { email = user.getEmail(); phone = user.getPhone(); }
    }

    public String getFirstName() { return firstName != null ? firstName : user == null ? null : user.getFirstName(); }
    public String getLastName() { return lastName != null ? lastName : user == null ? null : user.getLastName(); }
    /** The personal address belongs to the account and is shared by all school records. */
    public String getEmail() { return user == null ? null : user.getEmail(); }
    public String getPhone() { return firstName == null && lastName == null && user != null ? user.getPhone() : phone; }
    public String getFullName() {
        return (java.util.Objects.toString(getFirstName(), "") + " " + java.util.Objects.toString(getLastName(), "")).trim();
    }

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;
}
