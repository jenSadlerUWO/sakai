package org.sakaiproject.gradebookng.business.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;

import org.sakaiproject.service.gradebook.shared.CategoryDefinition;
import org.apache.commons.validator.routines.DoubleValidator;
import org.sakaiproject.util.ResourceLoader;

@Slf4j
public class FormatHelper {

	private static ResourceLoader rl = new ResourceLoader();

	/**
	 * The value is a double (ie 12.34542) that needs to be formatted as a percentage with two decimal places precision. And drop off any .0
	 * if no decimal places.
	 *
	 * @param score as a double
	 * @return double to decimal places
	 */
	public static String formatDoubleToDecimal(final Double score) {
		return formatDoubleToDecimal(score, 2);
	}

	/**
	 * The value is a double (ie 12.34542) that needs to be formatted as a percentage with 'n' decimal places precision. And drop off any .0
	 * if no decimal places.
	 *
	 * @param score as a double
	 * @param n as an int
	 * @return double to n decimal places
	 */
	private static String formatDoubleToDecimal(final Double score, final int n) {
		final NumberFormat df = NumberFormat.getInstance();
		df.setMinimumFractionDigits(0);
		df.setMaximumFractionDigits(n);
		df.setGroupingUsed(false);
		df.setRoundingMode(RoundingMode.HALF_DOWN);

		return formatGrade(df.format(score));
	}

	/**
	 * Convert a double score to match the number of decimal places exhibited in the
	 * toMatch string representation of a number
	 *
	 * @param score as a double
	 * @param toMatch the number as a string
	 * @return double to decimal places
	 */
	public static String formatDoubleToMatch(final Double score, final String toMatch) {
		int numberOfDecimalPlaces = 0;

		if (toMatch.indexOf(".") >= 0) {
			numberOfDecimalPlaces = toMatch.split("\\.")[1].length();
		}

		if (toMatch.indexOf(",") >= 0) {
			numberOfDecimalPlaces = toMatch.split("\\,")[1].length();
		}

		return FormatHelper.formatDoubleToDecimal(score, numberOfDecimalPlaces);
	}

	/**
	 * The value is a double (ie 12.34) that needs to be formatted as a percentage with two decimal places precision.
	 *
	 * @param score as a double
	 * @return percentage to decimal places with a '%' for good measure
	 */
	public static String formatDoubleAsPercentage(final Double score) {
		// TODO does the % need to be internationalised?
		return formatDoubleToDecimal(score) + "%";
	}

	/**
	 * Format the given string as a percentage with two decimal precision. String should be something that can be converted to a number.
	 *
	 * @param string string representation of the number
	 * @return percentage to decimal places with a '%' for good measure
	 */
	public static String formatStringAsPercentage(final String string) {
		if (StringUtils.isBlank(string)) {
			return null;
		}

		final BigDecimal decimal = new BigDecimal(string).setScale(2, RoundingMode.HALF_DOWN);

		return formatDoubleAsPercentage(decimal.doubleValue());
	}

	/**
	 * Format a grade, e.g. 00 => 0 0001 => 1 1.0 => 1 1.25 => 1.25
	 *
	 * @param grade
	 * @return
	 */
	public static String formatGrade(final String grade) {
		if (StringUtils.isBlank(grade)) {
			return "";
		}

		String s = null;
		try {
			final Double d = Double.parseDouble(grade);

			final DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(rl.getLocale());
			df.setMinimumFractionDigits(0);
			df.setGroupingUsed(false);

			s = df.format(d);
		} catch (final NumberFormatException e) {
			log.debug("Bad format, returning original string: " + grade);
			s = grade;
		}

		return StringUtils.removeEnd(s, ".0");
	}

	/**
	 * Format a grade, e.g. 00 => 0 0001 => 1 1.0 => 1 1.25 => 1.25
	 *
	 * @param grade - string representation of a grade
	 * @param locale
	 * @return
	 */
	public static String formatGradeForDisplay(final Double grade, final Locale locale) {
		return formatGradeForDisplay(formatDoubleToDecimal(grade), locale);
	}

	/**
	 * Format a grade from the root locale for display using the user's locale
	 *
	 * @param grade - string representation of a grade
	 * @param locale
	 * @return
	 */
	public static String formatGradeForDisplay(final String grade, final Locale locale) {
		if (StringUtils.isBlank(grade)) {
			return "";
		}

		String s = null;
		try {
			final DecimalFormat dfParse = (DecimalFormat) NumberFormat.getInstance(locale);
			dfParse.setParseBigDecimal(true);
			final BigDecimal d = (BigDecimal) dfParse.parse(grade);

			final DecimalFormat dfFormat = (DecimalFormat) NumberFormat.getInstance(locale);
			dfFormat.setMinimumFractionDigits(0);
			dfFormat.setMaximumFractionDigits(2);
			dfFormat.setGroupingUsed(true);
			s = dfFormat.format(d);
		} catch (final NumberFormatException e) {
			log.debug("Bad format, returning original string: " + grade);
			s = grade;
		} catch (final ParseException e) {
			log.debug("Bad format, returning original string: " + grade);
			s = grade;
		}

		return StringUtils.removeEnd(s, ".0");
	}

	/**
	 * Format a date e.g. MM/dd/yyyy
	 *
	 * @param date
	 * @return
	 */
	private static String formatDate(final Date date) {
		final String dateFormatString = MessageHelper.getString("format.date");
		final SimpleDateFormat df = new SimpleDateFormat(dateFormatString);
		return df.format(date);
	}

	/**
	 * Format a date but return ifNull if null
	 *
	 * @param date
	 * @param ifNull
	 * @return
	 */
	public static String formatDate(final Date date, final String ifNull) {
		if (date == null) {
			return ifNull;
		}

		return formatDate(date);
	}

	/**
	 * Format a date with a time e.g. MM/dd/yyyy HH:mm
	 *
	 * @param date
	 * @return
	 */
	public static String formatDateTime(final Date date) {
		final String dateTimeFormatString = MessageHelper.getString("format.datetime");
		final SimpleDateFormat df = new SimpleDateFormat(dateTimeFormatString);
		return df.format(date);
	}

	/**
	 * Abbreviate a string via {@link StringUtils#abbreviateMiddle(String, String, int)}
	 *
	 * Set at 45 chars
	 *
	 * @param s String to abbreviate
	 * @return abbreviated string or full string if it was shorter than the setting
	 */
	public static String abbreviateMiddle(final String s) {
		return StringUtils.abbreviateMiddle(s, "...", 45);
	}
	
	public static List<String> formatCategoryDropInfo(CategoryDefinition category)
	{
		if (category == null)
		{
			return Collections.emptyList();
		}
		
		int dropHighest = category.getDropHighest() == null ? 0 : category.getDropHighest();
		int dropLowest = category.getDrop_lowest() == null ? 0 : category.getDrop_lowest();
		int keepHighest = category.getKeepHighest() == null ? 0 : category.getKeepHighest();
		
		if (dropHighest == 0 && dropLowest == 0 && keepHighest == 0)
		{
			return Collections.emptyList();
		}
		
		List<String> info = new ArrayList<>(2);
		if (dropHighest > 0)
		{
			info.add(MessageHelper.getString("label.category.drophighest", dropHighest));
		}
		if (dropLowest > 0)
		{
			info.add(MessageHelper.getString("label.category.droplowest", dropLowest));
		}
		if (keepHighest > 0)
		{
			info.add(MessageHelper.getString("label.category.keephighest", keepHighest));
		}
		
		return info;
	}
	

	/**
	 * Validate if a string is a valid Double using the specified Locale.
	 *
	 * @param value - The value validation is being performed on.
	 * @return true if the value is valid
	 */
	public static boolean isValidDouble(String value) {
		DoubleValidator dv = new DoubleValidator();
		return dv.isValid(value, rl.getLocale());
	}

	/**
	 * Validate/convert a Double using the user's Locale.
	 *
	 * @param value - The value validation is being performed on.
	 * @return The parsed Double if valid or null if invalid.
	 */
	public static Double validateDouble(String value) {
		DoubleValidator dv = new DoubleValidator();
		return dv.validate(value, rl.getLocale());
	}

	/**
	 * Format a grade, e.g. 00 => 0 0001 => 1 1.0 => 1 1.25 => 1.25
	 *
	 * @param grade - string representation of a grade
	 * @return
	 */
	public static String formatGradeForDisplay(final Double grade) {
		return formatGradeForDisplay(formatDoubleToDecimal(grade));
	}

	/**
	 * Format a grade from the root locale for display using the user's locale
	 *
	 * @param grade - string representation of a grade
	 * @return
	 */
	public static String formatGradeForDisplay(final String grade) {
		if (StringUtils.isBlank(grade)) {
			return "";
		}

		String s;
		try {
			final DecimalFormat dfParse = (DecimalFormat) NumberFormat.getInstance(Locale.ROOT);
			dfParse.setParseBigDecimal(true);
			final BigDecimal d = (BigDecimal) dfParse.parse(grade);

			final DecimalFormat dfFormat = (DecimalFormat) NumberFormat.getInstance(rl.getLocale());
			dfFormat.setMinimumFractionDigits(0);
			dfFormat.setMaximumFractionDigits(2);
			dfFormat.setGroupingUsed(true);
			s = dfFormat.format(d);
		} catch (final NumberFormatException | ParseException e) {
			log.debug("Bad format, returning original string: {}", grade);
			s = grade;
		}

		return StringUtils.removeEnd(s, ".0");
	}
}
