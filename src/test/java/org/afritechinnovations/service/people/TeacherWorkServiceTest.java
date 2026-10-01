package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.TeacherPaymentRequest;
import org.afritechinnovations.dto.people.TeacherRateRequest;
import org.afritechinnovations.dto.people.TeacherSessionRequest;
import org.afritechinnovations.dto.people.TeacherSlotRequest;
import org.afritechinnovations.dto.people.TeacherWorkDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.model.people.TeacherExtraHour;
import org.afritechinnovations.model.people.TeacherPayment;
import org.afritechinnovations.model.people.TeacherRate;
import org.afritechinnovations.model.people.TeacherRateType;
import org.afritechinnovations.model.people.TeacherScheduleSlot;
import org.afritechinnovations.model.people.TeacherSessionRecord;
import org.afritechinnovations.model.people.TeacherSessionStatus;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.TeacherExtraHourRepository;
import org.afritechinnovations.repository.people.TeacherPaymentRepository;
import org.afritechinnovations.repository.people.TeacherRateRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.people.TeacherScheduleSlotRepository;
import org.afritechinnovations.repository.people.TeacherSessionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherWorkServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final LocalDate SEPT = LocalDate.of(2026, 9, 1);

    @Mock TeacherRepository teacherRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock ClassSubjectTeacherRepository classSubjectTeacherRepository;
    @Mock TeacherRateRepository teacherRateRepository;
    @Mock TeacherScheduleSlotRepository slotRepository;
    @Mock TeacherSessionRecordRepository sessionRecordRepository;
    @Mock TeacherExtraHourRepository extraHourRepository;
    @Mock TeacherPaymentRepository teacherPaymentRepository;
    @Mock org.afritechinnovations.service.common.EmailService emailService;
    @InjectMocks TeacherWorkService service;

    private School school;
    private Teacher teacher;
    private SchoolClass schoolClass;
    private TeacherScheduleSlot mondaySlot;
    private List<TeacherSessionRecord> records;
    private List<TeacherPayment> payments;

    @BeforeEach
    void setUp() {
        // Aujourd'hui : mercredi 30 septembre 2026.
        service.setClock(Clock.fixed(LocalDate.of(2026, 9, 30).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        school = School.builder().id(1L).owner(User.builder().id(OWNER_ID).build()).build();
        teacher = Teacher.builder().id(5L).school(school)
                .user(User.builder().id(50L).firstName("Awa").lastName("Traoré").email("awa@ecole.bf").build()).build();
        AcademicYear year = AcademicYear.builder().id(2L).startDate(SEPT).endDate(LocalDate.of(2027, 7, 31)).build();
        schoolClass = SchoolClass.builder().id(3L).name("6e A").school(school).academicYear(year).build();
        // Lundis de septembre 2026 : 7, 14, 21, 28 -> 4 séances de 2 h = 8 h prévues.
        mondaySlot = TeacherScheduleSlot.builder().id(7L).teacher(teacher).schoolClass(schoolClass).dayOfWeek(1)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).effectiveFrom(SEPT).build();
        records = new ArrayList<>(List.of(
                record(LocalDate.of(2026, 9, 7), TeacherSessionStatus.PRESENT),
                record(LocalDate.of(2026, 9, 14), TeacherSessionStatus.ABSENT),
                record(LocalDate.of(2026, 9, 21), TeacherSessionStatus.PRESENT)));
        payments = new ArrayList<>();

        when(teacherRepository.findById(5L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(teacher));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(schoolClass));
        when(classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherId(3L, 5L)).thenReturn(true);
        when(classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherIdAndActiveTrue(3L, 5L)).thenReturn(true);
        when(slotRepository.findAllWithClassByTeacherId(5L)).thenReturn(List.of(mondaySlot));
        when(slotRepository.findById(7L)).thenReturn(Optional.of(mondaySlot));
        when(sessionRecordRepository.findByTeacherIdAndDateBetween(eq(5L), any(), any())).thenAnswer(inv -> records);
        when(sessionRecordRepository.findBySlotIdAndSessionDate(eq(7L), any())).thenAnswer(inv -> records.stream()
                .filter(r -> r.getSessionDate().equals(inv.getArgument(1))).findFirst());
        when(extraHourRepository.findByTeacherIdAndDateBetween(eq(5L), any(), any())).thenReturn(List.of(
                TeacherExtraHour.builder().id(9L).teacher(teacher).schoolClass(schoolClass)
                        .workDate(LocalDate.of(2026, 9, 10)).hours(new BigDecimal("1.50")).build()));
        when(teacherPaymentRepository.findByTeacherIdAndPayMonthOrderByPaymentDateAscIdAsc(eq(5L), any()))
                .thenAnswer(inv -> payments.stream().filter(p -> p.getPayMonth().equals(inv.getArgument(1))).toList());
        when(teacherPaymentRepository.findByTeacherIdAndPayMonthGreaterThanEqual(eq(5L), any()))
                .thenAnswer(inv -> payments.stream()
                        .filter(p -> !p.getPayMonth().isBefore(inv.getArgument(1))).toList());
        when(teacherPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(teacherRateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void monthlyRateIsProratedOnAttendedAndExtraHoursOverPlannedHours() {
        monthlyRate("80000.00");
        payments.add(payment(new BigDecimal("20000.00")));

        TeacherWorkDto.TeacherDetail detail = service.getTeacherDetail(5L, "2026-09", OWNER_ID, false);
        TeacherWorkDto.MonthSummary month = detail.month();

        assertEquals("MONTHLY", detail.rateType());
        assertEquals(0, new BigDecimal("8.00").compareTo(month.plannedHours()));
        assertEquals(0, new BigDecimal("4.00").compareTo(month.workedHours()));
        assertEquals(0, new BigDecimal("2.00").compareTo(month.absenceHours()));
        assertEquals(0, new BigDecimal("1.50").compareTo(month.extraHours()));
        // 80000 x (4 h + 1,5 h) / 8 h
        assertEquals(new BigDecimal("55000.00"), month.amountDue());
        assertEquals(new BigDecimal("20000.00"), month.paid());
        assertEquals(new BigDecimal("35000.00"), month.remaining());
        assertEquals(List.of("PRESENT", "ABSENT", "PRESENT", "PENDING"),
                month.sessions().stream().map(TeacherWorkDto.SessionInfo::status).toList());
        assertEquals("08:00", month.sessions().get(0).startTime());
        assertEquals(1, month.perClass().size());
        assertEquals(0, new BigDecimal("1.50").compareTo(month.perClass().get(0).extraHours()));
    }

    @Test
    void hourlyRatePaysWorkedAndExtraHoursOnly() {
        TeacherRate hourly = TeacherRate.builder().rateType(TeacherRateType.HOURLY).amount(new BigDecimal("2000.00")).build();
        assertEquals(new BigDecimal("11000.00"),
                TeacherWorkService.computeAmountDue(Optional.of(hourly), 480, 240, new BigDecimal("1.50")));
        assertEquals(new BigDecimal("0.00"),
                TeacherWorkService.computeAmountDue(Optional.empty(), 480, 240, BigDecimal.ONE));
    }

    @Test
    void monthlyPaymentIsRejectedWhenNoHourIsPlanned() {
        monthlyRate("80000.00");
        when(slotRepository.findAllWithClassByTeacherId(5L)).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addPayment(5L, paymentRequest("2026-09", "10.00"), OWNER_ID, false));
        assertTrue(ex.getMessage().contains("Aucune heure"));
        verify(teacherPaymentRepository, never()).save(any());
    }

    @Test
    void paymentCannotExceedRemainingBalance() {
        monthlyRate("80000.00");
        payments.add(payment(new BigDecimal("50000.00")));

        assertThrows(IllegalArgumentException.class,
                () -> service.addPayment(5L, paymentRequest("2026-09", "5000.01"), OWNER_ID, false));
        verify(teacherPaymentRepository, never()).save(any());

        TeacherWorkDto.PaymentInfo saved = service.addPayment(5L, paymentRequest("2026-09", "5000"), OWNER_ID, false);
        assertEquals(new BigDecimal("5000.00"), saved.amount());
    }

    @Test
    void paymentIsRejectedWithoutRate() {
        when(teacherRateRepository.findFirstByTeacherIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.addPayment(5L, paymentRequest("2026-09", "10"), OWNER_ID, false));
    }

    @Test
    void rateStartsAtMonthStartAndCannotRewritePaidMonths() {
        TeacherRateRequest request = new TeacherRateRequest();
        request.setType("HOURLY");
        request.setAmount(new BigDecimal("1500"));
        request.setEffectiveFrom(LocalDate.of(2026, 10, 17));

        TeacherWorkDto.RateInfo rate = service.addRate(5L, request, OWNER_ID, false);
        assertEquals(LocalDate.of(2026, 10, 1), rate.effectiveFrom());

        when(teacherPaymentRepository.existsByTeacherIdAndPayMonthGreaterThanEqual(5L, SEPT)).thenReturn(true);
        request.setEffectiveFrom(LocalDate.of(2026, 9, 15));
        assertThrows(IllegalArgumentException.class, () -> service.addRate(5L, request, OWNER_ID, false));
    }

    @Test
    void otherOwnersAreDeniedOnReadsAndWrites() {
        assertThrows(AccessDeniedException.class, () -> service.getTeacherDetail(5L, "2026-09", 99L, false));
        assertThrows(AccessDeniedException.class, () -> service.listClassTeachers(3L, 99L, false));
        assertThrows(AccessDeniedException.class,
                () -> service.addPayment(5L, paymentRequest("2026-09", "10"), 99L, false));
        assertThrows(AccessDeniedException.class, () -> service.deletePayment(5L, 1L, 99L, false));
    }

    @Test
    void slotRequiresClassOfSameSchoolAndTeacherAssignment() {
        School otherSchool = School.builder().id(2L).owner(User.builder().id(OWNER_ID).build()).build();
        SchoolClass foreignClass = SchoolClass.builder().id(4L).name("CM2").school(otherSchool)
                .academicYear(schoolClass.getAcademicYear()).build();
        when(schoolClassRepository.findById(4L)).thenReturn(Optional.of(foreignClass));
        assertThrows(AccessDeniedException.class, () -> service.addSlot(5L, slotRequest(4L, 2, "08:00", "10:00"), OWNER_ID, false));

        when(classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherId(3L, 5L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.addSlot(5L, slotRequest(3L, 2, "08:00", "10:00"), OWNER_ID, false));
    }

    @Test
    void deactivatingClassTeacherKeepsAssignmentEndsSlotsAndCanBeReactivated() {
        org.afritechinnovations.model.academic.ClassSubjectTeacher assignment =
                org.afritechinnovations.model.academic.ClassSubjectTeacher.builder().id(11L).schoolClass(schoolClass).teacher(teacher)
                .subject(org.afritechinnovations.model.academic.Subject.builder().id(2L).name("Maths").build()).build();
        when(classSubjectTeacherRepository.findBySchoolClassIdAndTeacherId(3L, 5L)).thenReturn(List.of(assignment));

        TeacherWorkDto.TeacherInfo info = service.deactivateClassTeacher(3L, 5L, OWNER_ID, false);
        assertEquals(Boolean.FALSE, info.activeInClass());
        assertEquals(false, assignment.isActive());
        assertEquals(LocalDate.of(2026, 9, 29), mondaySlot.getEffectiveTo());
        verify(classSubjectTeacherRepository, never()).delete(any());
        assertThrows(IllegalArgumentException.class, () -> service.deactivateClassTeacher(3L, 5L, OWNER_ID, false));

        when(classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherIdAndActiveTrue(3L, 5L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.addSlot(5L, slotRequest(3L, 2, "08:00", "10:00"), OWNER_ID, false));

        TeacherWorkDto.TeacherInfo reactivated = service.reactivateClassTeacher(3L, 5L, OWNER_ID, false);
        assertEquals(Boolean.TRUE, reactivated.activeInClass());
        assertEquals(true, assignment.isActive());
        assertNull(assignment.getDeactivatedAt());
    }

    @Test
    void sendsMonthlySummaryToTeacherEmail() {
        service.sendTeacherSummary(5L, "2026-09", OWNER_ID, false);
        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendText(eq("awa@ecole.bf"), org.mockito.ArgumentMatchers.contains("2026-09"), body.capture());
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().contains("Bonjour Awa Traoré"));
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().contains("Lundi 08:00-10:00 : 6e A"));
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().contains("Heures présentes : 4 h"));

        assertThrows(AccessDeniedException.class, () -> service.sendTeacherSummary(5L, "2026-09", 99L, false));
    }

    @Test
    void slotRejectsOverlapAndInvertedTimes() {
        assertThrows(IllegalArgumentException.class, () -> service.addSlot(5L, slotRequest(3L, 1, "09:00", "11:00"), OWNER_ID, false));
        assertThrows(IllegalArgumentException.class, () -> service.addSlot(5L, slotRequest(3L, 2, "10:00", "09:00"), OWNER_ID, false));
        TeacherWorkDto.SlotInfo slot = service.addSlot(5L, slotRequest(3L, 1, "10:00", "11:30"), OWNER_ID, false);
        assertEquals("10:00", slot.startTime());
        assertNull(slot.effectiveTo());
    }

    @Test
    void archivingSlotKeepsHistoryUntilYesterday() {
        service.archiveSlot(5L, 7L, OWNER_ID, false);
        assertEquals(LocalDate.of(2026, 9, 29), mondaySlot.getEffectiveTo());
        verify(slotRepository, never()).delete(any());

        TeacherScheduleSlot fresh = TeacherScheduleSlot.builder().id(8L).teacher(teacher).schoolClass(schoolClass)
                .dayOfWeek(3).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .effectiveFrom(LocalDate.of(2026, 9, 30)).build();
        when(slotRepository.findById(8L)).thenReturn(Optional.of(fresh));
        service.archiveSlot(5L, 8L, OWNER_ID, false);
        verify(slotRepository).delete(fresh);
    }

    @Test
    void sessionMustMatchSlotDayAndNotBeInFuture() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSession(5L, sessionRequest(LocalDate.of(2026, 9, 8), "PRESENT"), OWNER_ID, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSession(5L, sessionRequest(LocalDate.of(2026, 10, 5), "PRESENT"), OWNER_ID, false));

        when(sessionRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TeacherWorkDto.SessionInfo session = service.recordSession(5L,
                sessionRequest(LocalDate.of(2026, 9, 28), "PRESENT"), OWNER_ID, false);
        assertEquals("PRESENT", session.status());
        assertEquals(0, new BigDecimal("2").compareTo(session.hours()));
    }

    @Test
    void attendanceChangeCannotPushPaidMonthIntoOverpayment() {
        monthlyRate("80000.00");
        payments.add(payment(new BigDecimal("55000.00")));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordSession(5L, sessionRequest(LocalDate.of(2026, 9, 21), "ABSENT"), OWNER_ID, false));
    }

    private void monthlyRate(String amount) {
        when(teacherRateRepository.findFirstByTeacherIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(anyLong(), any()))
                .thenReturn(Optional.of(TeacherRate.builder().id(1L).teacher(teacher).rateType(TeacherRateType.MONTHLY)
                        .amount(new BigDecimal(amount)).effectiveFrom(SEPT).build()));
    }

    private TeacherSessionRecord record(LocalDate date, TeacherSessionStatus status) {
        return TeacherSessionRecord.builder().slot(mondaySlot).sessionDate(date).status(status).build();
    }

    private TeacherPayment payment(BigDecimal amount) {
        return TeacherPayment.builder().id((long) payments.size() + 1).teacher(teacher).payMonth(SEPT)
                .paymentDate(LocalDate.of(2026, 9, 25)).amount(amount).build();
    }

    private static TeacherPaymentRequest paymentRequest(String month, String amount) {
        TeacherPaymentRequest request = new TeacherPaymentRequest();
        request.setMonth(month);
        request.setDate(LocalDate.of(2026, 9, 30));
        request.setAmount(new BigDecimal(amount));
        request.setReference("VIR-1");
        return request;
    }

    private static TeacherSlotRequest slotRequest(Long classId, int day, String start, String end) {
        TeacherSlotRequest request = new TeacherSlotRequest();
        request.setClassId(classId);
        request.setDayOfWeek(day);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setEffectiveFrom(SEPT);
        return request;
    }

    private static TeacherSessionRequest sessionRequest(LocalDate date, String status) {
        TeacherSessionRequest request = new TeacherSessionRequest();
        request.setSlotId(7L);
        request.setDate(date);
        request.setStatus(status);
        return request;
    }
}