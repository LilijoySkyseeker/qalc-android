#include "bridge_core.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <iostream>
#include <libqalculate/includes.h>

namespace fs = std::filesystem;

static int failures = 0;

#define CHECK(cond)                                                              \
	do {                                                                         \
		if(!(cond)) {                                                            \
			std::cerr << __FILE__ << ":" << __LINE__ << ": FAILED " #cond "\n"; \
			failures++;                                                          \
		}                                                                        \
	} while(0)

#define CHECK_TEXT(expr, expected)                                                       \
	do {                                                                                 \
		CalcResult r_ = engine_calculate(expr, 2000);                                    \
		if(r_.text != (expected)) {                                                      \
			std::cerr << __FILE__ << ":" << __LINE__ << ": FAILED " << (expr) << " -> \"" \
			          << r_.text << "\", expected \"" << (expected) << "\"\n";          \
			failures++;                                                                  \
		}                                                                                \
	} while(0)

static EngineSettings settings(int approximation, int fraction = FRACTION_DECIMAL, int denominator = 0) {
	return EngineSettings{ANGLE_UNIT_RADIANS, approximation, 10, fraction, POST_CONVERSION_OPTIMAL, denominator};
}

static fs::path fresh_dir() {
	char tmpl[] = "/tmp/bridge_test.XXXXXX";
	return fs::path(mkdtemp(tmpl));
}

static void test_units(const fs::path &dir) {
	engine_init(dir.string());
	engine_apply(settings(APPROXIMATION_APPROXIMATE));
	CalcResult r = engine_calculate("5 ft + 30 cm to in", 2000);
	CHECK(r.ok);
	CHECK(r.text == "71.81102362 in");
	CHECK_TEXT("1.6 m to ft", "5 ft + 2.992125984 in");
	CHECK_TEXT("5 ft 3.5 in to cm", "161.29 cm");
	CHECK_TEXT("sqrt(2)", "1.414213562");
}

static void test_errors_and_warnings() {
	CalcResult r = engine_calculate("sqrt(", 2000);
	CHECK(!r.ok);
	CHECK(!r.messages.empty());

	r = engine_calculate("10 / 0", 2000);
	CHECK(r.ok);
	bool found = false;
	for(auto &m : r.messages) found |= m == "Division by zero.";
	CHECK(found);
}

static void test_timeout() {
	auto start = std::chrono::steady_clock::now();
	CalcResult r = engine_calculate("factorial(100000000)", 200);
	auto elapsed = std::chrono::steady_clock::now() - start;
	CHECK(r.aborted);
	CHECK(!r.ok);
	CHECK(elapsed < std::chrono::seconds(2));
	// the engine still works afterwards
	CHECK_TEXT("1 + 1", "2");
}

static void test_settings() {
	engine_apply(settings(APPROXIMATION_EXACT, FRACTION_FRACTIONAL));
	CHECK_TEXT("1/3 + 1/4", "7/12");
	engine_apply(settings(APPROXIMATION_APPROXIMATE, FRACTION_COMBINED_FIXED_DENOMINATOR, 16));
	CHECK_TEXT("1 m to ft", "3 ft + (3 + 6/16) in");
	engine_apply(settings(APPROXIMATION_APPROXIMATE));
}

static void test_rates(const fs::path &dir) {
	auto sources = engine_rate_sources();
	CHECK(sources.size() == 4);
	for(auto &s : sources) {
		CHECK(s.url.rfind("https://", 0) == 0);
		CHECK(s.path.rfind(dir.string(), 0) == 0);
	}
	fs::create_directories(fs::path(sources[0].path).parent_path());
	fs::copy_file(FIXTURES "/eurofxref-daily.xml", sources[0].path, fs::copy_options::overwrite_existing);
	CHECK(engine_reload_rates());
	CHECK_TEXT("1 EUR to USD", "$1.100000000");
}

static void test_definitions_persist(const fs::path &dir) {
	CHECK(engine_calculate("y := 7", 2000).ok);
	CHECK(engine_save_definitions());
	engine_init(dir.string());
	engine_apply(settings(APPROXIMATION_APPROXIMATE));
	CHECK_TEXT("2y", "14");
}

int main() {
	fs::path dir = fresh_dir();
	test_units(dir);
	test_errors_and_warnings();
	test_timeout();
	test_settings();
	test_rates(dir);
	test_definitions_persist(dir);
	fs::remove_all(dir);
	if(failures) {
		std::cerr << failures << " check(s) failed\n";
		return 1;
	}
	std::cout << "all bridge tests passed\n";
	return 0;
}
