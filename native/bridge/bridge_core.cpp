#include "bridge_core.h"

#include <atomic>
#include <chrono>
#include <cstdlib>
#include <libqalculate/qalculate.h>

namespace {

EvaluationOptions evalops;
PrintOptions printops;
// calculateAndPrint reports neither a timeout nor an abort (it prints the
// partly evaluated expression), so both are tracked here.
std::atomic<bool> abort_requested{false};

void set_defaults() {
	// Mirrors the defaults of the qalc command-line tool (src/qalc.cc), so
	// results look like desktop Qalculate's.
	printops = default_print_options;
	printops.min_exp = EXP_PRECISION;
	printops.negative_exponents = false;
	printops.sort_options.minus_last = true;
	printops.indicate_infinite_series = false;
	printops.show_ending_zeroes = true;
	printops.digit_grouping = DIGIT_GROUPING_NONE;
	printops.rounding = ROUNDING_HALF_AWAY_FROM_ZERO;
	printops.number_fraction_format = FRACTION_DECIMAL;
	printops.restrict_fraction_length = false;
	printops.abbreviate_names = true;
	printops.use_unicode_signs = true;
	printops.use_unit_prefixes = true;
	printops.spacious = true;
	printops.short_multiplication = true;
	printops.limit_implicit_multiplication = false;
	printops.place_units_separately = true;
	printops.use_all_prefixes = false;
	printops.excessive_parenthesis = false;
	printops.allow_non_usable = false;
	printops.lower_case_numbers = false;
	printops.exp_display = EXP_UPPERCASE_E;
	printops.base_display = BASE_DISPLAY_NORMAL;
	printops.twos_complement = true;
	printops.hexadecimal_twos_complement = false;
	printops.division_sign = DIVISION_SIGN_SLASH;
	printops.multiplication_sign = MULTIPLICATION_SIGN_X;
	printops.allow_factorization = false;
	printops.spell_out_logical_operators = true;
	printops.interval_display = INTERVAL_DISPLAY_SIGNIFICANT_DIGITS;

	evalops = default_user_evaluation_options;
	evalops.parse_options.parsing_mode = PARSING_MODE_ADAPTIVE;
	evalops.approximation = APPROXIMATION_TRY_EXACT;
	evalops.sync_units = true;
	evalops.structuring = STRUCTURING_SIMPLIFY;
	evalops.parse_options.unknowns_enabled = false;
	evalops.parse_options.read_precision = DONT_READ_PRECISION;
	evalops.parse_options.base = BASE_DECIMAL;
	evalops.allow_complex = true;
	evalops.allow_infinite = true;
	evalops.auto_post_conversion = POST_CONVERSION_OPTIMAL;
	evalops.assume_denominators_nonzero = true;
	evalops.warn_about_denominators_assumed_nonzero = true;
	evalops.parse_options.angle_unit = ANGLE_UNIT_RADIANS;
	evalops.parse_options.dot_as_separator = CALCULATOR->default_dot_as_separator;
	evalops.parse_options.comma_as_separator = false;
	evalops.mixed_units_conversion = MIXED_UNITS_CONVERSION_DEFAULT;
	evalops.complex_number_form = COMPLEX_NUMBER_FORM_RECTANGULAR;
	evalops.local_currency_conversion = true;
	evalops.interval_calculation = INTERVAL_CALCULATION_VARIANCE_FORMULA;
	CALCULATOR->setPrecision(10);
}

}  // namespace

void engine_init(const std::string &userDir) {
	setenv("QALCULATE_USER_DIR", userDir.c_str(), 1);
	delete CALCULATOR;
	new Calculator();
	CALCULATOR->loadExchangeRates();
	CALCULATOR->loadGlobalDefinitions();
	CALCULATOR->loadLocalDefinitions();
	set_defaults();
}

CalcResult engine_calculate(const std::string &expr, int timeoutMs) {
	CalcResult r;
	CALCULATOR->clearMessages();
	abort_requested = false;
	auto start = std::chrono::steady_clock::now();
	r.text = CALCULATOR->calculateAndPrint(CALCULATOR->unlocalizeExpression(expr, evalops.parse_options), timeoutMs,
	                                       evalops, printops, &r.parsed);
	auto elapsed = std::chrono::steady_clock::now() - start;
	r.aborted = abort_requested || elapsed >= std::chrono::milliseconds(timeoutMs);
	bool error = false;
	for(CalculatorMessage *m = CALCULATOR->message(); m; m = CALCULATOR->nextMessage()) {
		r.messages.push_back(m->message());
		error |= m->type() == MESSAGE_ERROR;
	}
	r.ok = !error && !r.aborted;
	return r;
}

void engine_abort() {
	abort_requested = true;
	CALCULATOR->abort();
}

void engine_apply(const EngineSettings &s) {
	evalops.parse_options.angle_unit = (AngleUnit) s.angleUnit;
	evalops.approximation = (ApproximationMode) s.approximation;
	evalops.auto_post_conversion = (AutoPostConversion) s.autoConversion;
	printops.number_fraction_format = (NumberFractionFormat) s.fractionFormat;
	CALCULATOR->setFixedDenominator(s.fixedDenominator > 0 ? s.fixedDenominator : 2);
	CALCULATOR->setPrecision(s.precision);
}

bool engine_save_definitions() {
	// "x := 5" creates variables in the Temporary category, which desktop
	// Qalculate keeps only for the session. Android ends sessions whenever it
	// likes, so make them permanent before saving.
	std::string temporary = CALCULATOR->temporaryCategory();
	for(Variable *v : CALCULATOR->variables) {
		if(v->isLocal() && v->category() == temporary) v->setCategory("");
	}
	for(MathFunction *f : CALCULATOR->functions) {
		if(f->isLocal() && f->category() == temporary) f->setCategory("");
	}
	return CALCULATOR->saveDefinitions();
}

std::vector<RateSource> engine_rate_sources() {
	std::vector<RateSource> sources;
	for(int i = 1; i <= 4; i++) {
		sources.push_back({CALCULATOR->getExchangeRatesUrl(i), CALCULATOR->getExchangeRatesFileName(i)});
	}
	return sources;
}

bool engine_reload_rates() {
	return CALCULATOR->loadExchangeRates();
}
