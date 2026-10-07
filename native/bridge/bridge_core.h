// Plain C++ facade over libqalculate. The only code that touches the engine;
// used by the JNI layer on Android and by the host tests.
#pragma once

#include <string>
#include <vector>

struct CalcResult {
	std::string text, parsed;
	std::vector<std::string> messages;  // errors and warnings, in order
	bool ok = false;                    // no error message and not aborted
	bool aborted = false;
};

// Values are libqalculate enum ints: AngleUnit, ApproximationMode,
// NumberFractionFormat, AutoPostConversion. fixedDenominator > 0 is passed
// to Calculator::setFixedDenominator().
struct EngineSettings {
	int angleUnit, approximation, precision, fractionFormat, autoConversion, fixedDenominator;
};

struct RateSource {
	std::string url, path;
};

// Creates the engine (replacing any previous one) with userDir as the place
// for user definitions and exchange-rate files.
void engine_init(const std::string &userDir);
CalcResult engine_calculate(const std::string &expr, int timeoutMs);
// Safe to call from any thread.
void engine_abort();
void engine_apply(const EngineSettings &settings);
bool engine_save_definitions();
std::vector<RateSource> engine_rate_sources();
bool engine_reload_rates();
