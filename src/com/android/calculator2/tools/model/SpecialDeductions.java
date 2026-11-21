/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The seven Chinese special additional deductions (专项附加扣除, effective 2019-01-01), reduced to
 * a monthly total for the cumulative-withholding calculation. Pure data; independent of Android.
 * <ul>
 * <li>Children's education: 1000/month per child, either one parent at 100% or both at 50%.</li>
 * <li>Continuing education: 400/month for academic study (max 48 months), or 3600 in the year a
 *     professional qualification certificate is obtained (spread as 300/month here).</li>
 * <li>Major medical: the self-paid part above 15000 in a tax year, at actual cost capped at
 *     80000/year, spread over the year's months.</li>
 * <li>Mortgage interest: first home, 1000/month during repayment, one spouse at 100% or 50%.</li>
 * <li>Housing rent: 1500 / 1100 / 800 per month by city tier; mutually exclusive with mortgage
 *     interest - when both are set, mortgage interest wins and rent contributes nothing.</li>
 * <li>Elderly support: 2000/month for an only child; otherwise a shared amount up to
 *     1000/month.</li>
 * <li>Infant care under 3: 1000/month per child, one parent at 100% or both at 50%.</li>
 * </ul>
 */
public final class SpecialDeductions {

    /** How a per-child / per-home deduction is split between the two entitled parents. */
    public enum Share {
        FULL(BigDecimal.ONE),
        HALF(new BigDecimal("0.5"));

        private final BigDecimal mFactor;

        Share(BigDecimal factor) {
            mFactor = factor;
        }

        public BigDecimal factor() {
            return mFactor;
        }
    }

    /** Continuing-education variant. */
    public enum ContinuingEducation { NONE, ACADEMIC, CERTIFICATE_YEAR }

    /** Housing-rent city tier; the monthly amounts fixed by the 2019 rules. */
    public enum RentTier {
        NONE(BigDecimal.ZERO),
        TIER_1500(new BigDecimal("1500")),
        TIER_1100(new BigDecimal("1100")),
        TIER_800(new BigDecimal("800"));

        private final BigDecimal mMonthly;

        RentTier(BigDecimal monthly) {
            mMonthly = monthly;
        }

        public BigDecimal monthly() {
            return mMonthly;
        }
    }

    private static final BigDecimal MONTHS = new BigDecimal(12);
    private static final BigDecimal PER_CHILD_EDUCATION = new BigDecimal("1000");
    private static final BigDecimal ACADEMIC_MONTHLY = new BigDecimal("400");
    private static final BigDecimal CERTIFICATE_YEARLY = new BigDecimal("3600");
    private static final BigDecimal MEDICAL_FLOOR = new BigDecimal("15000");
    private static final BigDecimal MEDICAL_CAP = new BigDecimal("80000");
    private static final BigDecimal MORTGAGE_MONTHLY = new BigDecimal("1000");
    private static final BigDecimal ELDERLY_ONLY_CHILD = new BigDecimal("2000");
    private static final BigDecimal ELDERLY_SHARED_CAP = new BigDecimal("1000");
    private static final BigDecimal PER_INFANT = new BigDecimal("1000");

    public int educationChildren;
    public Share educationShare = Share.FULL;
    public ContinuingEducation continuing = ContinuingEducation.NONE;
    /** Self-paid major-medical cost recorded in the insurance system for the tax year. */
    public BigDecimal majorMedicalAnnualSelfPaid = BigDecimal.ZERO;
    public boolean mortgageInterest;
    public Share mortgageShare = Share.FULL;
    public RentTier rent = RentTier.NONE;
    public boolean elderlyOnlyChild;
    /** Monthly amount claimed when sharing elderly support with siblings; capped at 1000. */
    public BigDecimal elderlySharedMonthly = BigDecimal.ZERO;
    public int infantChildren;
    public Share infantShare = Share.FULL;

    /** Monthly deduction total under the rules above; never negative. */
    public BigDecimal monthlyTotal(MathContext mc) {
        BigDecimal total = BigDecimal.ZERO;

        total = total.add(PER_CHILD_EDUCATION
                .multiply(new BigDecimal(Math.max(0, educationChildren)), mc)
                .multiply(educationShare.factor(), mc), mc);

        if (continuing == ContinuingEducation.ACADEMIC) {
            total = total.add(ACADEMIC_MONTHLY, mc);
        } else if (continuing == ContinuingEducation.CERTIFICATE_YEAR) {
            total = total.add(CERTIFICATE_YEARLY.divide(MONTHS, mc), mc);
        }

        if (majorMedicalAnnualSelfPaid != null) {
            BigDecimal over = majorMedicalAnnualSelfPaid.subtract(MEDICAL_FLOOR, mc);
            if (over.signum() > 0) {
                total = total.add(over.min(MEDICAL_CAP).divide(MONTHS, mc), mc);
            }
        }

        if (mortgageInterest) {
            total = total.add(MORTGAGE_MONTHLY.multiply(mortgageShare.factor(), mc), mc);
        } else {
            // Rent is unavailable while mortgage interest is claimed (mutually exclusive).
            total = total.add(rent.monthly(), mc);
        }

        if (elderlyOnlyChild) {
            total = total.add(ELDERLY_ONLY_CHILD, mc);
        } else if (elderlySharedMonthly != null && elderlySharedMonthly.signum() > 0) {
            total = total.add(elderlySharedMonthly.min(ELDERLY_SHARED_CAP), mc);
        }

        total = total.add(PER_INFANT
                .multiply(new BigDecimal(Math.max(0, infantChildren)), mc)
                .multiply(infantShare.factor(), mc), mc);

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
