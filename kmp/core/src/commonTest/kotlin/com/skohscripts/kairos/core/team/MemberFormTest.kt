package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberFormTest {
    private val ok = MemberForm.Input(name = "Alex", role = "Dev", availabilityPercent = "80", hoursPerDay = "7")

    private fun errors(input: MemberForm.Input) = MemberForm.validate(input).errors

    @Test
    fun aValidFormIsCleanedAndConverted() {
        val r = MemberForm.validate(ok.copy(name = "  Alex  ", role = " Dev back ", hoursPerDay = " 7 "))
        assertEquals(MemberForm.Valid("Alex", "Dev back", 80, 7.0), r.valid)
        assertTrue(r.errors.isEmpty())
    }

    @Test
    fun theRoleIsOptional() {
        assertEquals("", MemberForm.validate(ok.copy(role = "   ")).valid!!.role)
    }

    @Test
    fun theNameIsRequiredAfterTrim() {
        assertEquals(mapOf("name" to FieldError(FieldErrorKind.REQUIRED)), errors(ok.copy(name = "   ")))
        assertEquals(mapOf("name" to FieldError(FieldErrorKind.REQUIRED)), errors(ok.copy(name = "")))
    }

    @Test
    fun availabilityMustBeAnIntegerBetweenOneAndOneHundred() {
        assertEquals(FieldError(FieldErrorKind.REQUIRED), errors(ok.copy(availabilityPercent = " "))["availabilityPercent"])
        assertEquals(FieldError(FieldErrorKind.NOT_INTEGER), errors(ok.copy(availabilityPercent = "abc"))["availabilityPercent"])
        assertEquals(FieldError(FieldErrorKind.NOT_INTEGER), errors(ok.copy(availabilityPercent = "80,5"))["availabilityPercent"])
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "1"), errors(ok.copy(availabilityPercent = "0"))["availabilityPercent"])
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "1"), errors(ok.copy(availabilityPercent = "-5"))["availabilityPercent"])
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "100"), errors(ok.copy(availabilityPercent = "101"))["availabilityPercent"])
        // Bornes incluses.
        assertEquals(1, MemberForm.validate(ok.copy(availabilityPercent = "1")).valid!!.availabilityPercent)
        assertEquals(100, MemberForm.validate(ok.copy(availabilityPercent = "100")).valid!!.availabilityPercent)
    }

    @Test
    fun hoursPerDayIsADecimalBetweenOneAndTwentyFour() {
        assertEquals(FieldError(FieldErrorKind.REQUIRED), errors(ok.copy(hoursPerDay = ""))["hoursPerDay"])
        assertEquals(FieldError(FieldErrorKind.NOT_NUMBER), errors(ok.copy(hoursPerDay = "sept"))["hoursPerDay"])
        assertEquals(FieldError(FieldErrorKind.NOT_NUMBER), errors(ok.copy(hoursPerDay = "NaN"))["hoursPerDay"])
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "1"), errors(ok.copy(hoursPerDay = "0,5"))["hoursPerDay"])
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "24"), errors(ok.copy(hoursPerDay = "24.5"))["hoursPerDay"])
        assertEquals(1.0, MemberForm.validate(ok.copy(hoursPerDay = "1")).valid!!.hoursPerDay)
        assertEquals(24.0, MemberForm.validate(ok.copy(hoursPerDay = "24")).valid!!.hoursPerDay)
    }

    @Test
    fun aDecimalCommaAndADecimalPointAreBothAccepted() {
        assertEquals(6.5, MemberForm.validate(ok.copy(hoursPerDay = "6,5")).valid!!.hoursPerDay)
        assertEquals(6.5, MemberForm.validate(ok.copy(hoursPerDay = "6.5")).valid!!.hoursPerDay)
    }

    @Test
    fun everyFieldErrorIsReportedAtOnceAndNothingIsHalfValid() {
        val r = MemberForm.validate(MemberForm.Input(name = "", availabilityPercent = "x", hoursPerDay = "99"))
        assertNull(r.valid)
        assertEquals(setOf("name", "availabilityPercent", "hoursPerDay"), r.errors.keys)
    }

    @Test
    fun theDefaultHoursAreTheWorkdayMinusOneHourBoundedToOneTwentyFour() {
        assertEquals(8.0, MemberForm.defaultHoursPerDay(Settings())) // 9-18 par défaut
        assertEquals(6.0, MemberForm.defaultHoursPerDay(Settings(workdayStartHour = 9, workdayEndHour = 16)))
        assertEquals(1.0, MemberForm.defaultHoursPerDay(Settings(workdayStartHour = 9, workdayEndHour = 10))) // 0 -> 1
        assertEquals(1.0, MemberForm.defaultHoursPerDay(Settings(workdayStartHour = 9, workdayEndHour = 9))) // -1 -> 1
        assertEquals(22.0, MemberForm.defaultHoursPerDay(Settings(workdayStartHour = 0, workdayEndHour = 23)))
    }

    @Test
    fun aNewFormStartsAtFullAvailabilityWithTheDefaultHours() {
        val input = MemberForm.newInput(Settings(workdayStartHour = 9, workdayEndHour = 17))
        assertEquals("", input.name)
        assertEquals("100", input.availabilityPercent)
        assertEquals("7", input.hoursPerDay)
        assertNotNull(MemberForm.validate(input.copy(name = "A")).valid)
    }

    @Test
    fun anExistingMemberRoundTripsThroughTheForm() {
        val t = kotlin.time.Instant.parse("2026-09-28T07:00:00Z")
        val m = TeamMember(1, "u", "Sam", "PO", 60, 6.5, createdAt = t, updatedAt = t)
        val input = MemberForm.inputOf(m)
        assertEquals(MemberForm.Input("Sam", "PO", "60", "6.5"), input)
        assertEquals(MemberForm.Valid("Sam", "PO", 60, 6.5), MemberForm.validate(input).valid)
    }

    @Test
    fun anAbsenceEndMustNotPrecedeItsStart() {
        val d = LocalDate(2026, 10, 12)
        assertTrue(MemberForm.isValidAbsence(d, d)) // un seul jour
        assertTrue(MemberForm.isValidAbsence(d, LocalDate(2026, 10, 16)))
        assertFalse(MemberForm.isValidAbsence(d, LocalDate(2026, 10, 11)))
    }
}
