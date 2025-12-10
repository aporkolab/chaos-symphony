#!/bin/bash
# Run contract tests in the correct order
# 1. First run consumer tests to generate pacts
# 2. Then run provider tests to verify pacts

set -e

echo "🏗️  Building project and running consumer tests to generate pacts..."

# Build the project and run consumer tests (this generates pacts)
cd "$(dirname "$0")/.."
mvn clean compile test-compile

echo "📝 Running consumer tests in orchestrator to generate pacts..."
cd orchestrator
mvn test -Dtest="*Pact*Test"

echo "✅ Consumer tests completed. Generated pacts should be in orchestrator/target/pacts/"

# List generated pacts
if [ -d "target/pacts" ]; then
    echo "Generated pact files:"
    ls -la target/pacts/
else
    echo "⚠️  Warning: No pacts directory found. Consumer tests may not have run successfully."
fi

echo "🔍 Running provider tests in payment-svc to verify pacts..."
cd ../payment-svc

# Run provider verification tests
mvn test -Dtest="*PactVerificationTest"

echo "✅ Contract tests completed successfully!"

echo ""
echo "📋 Contract Test Summary:"
echo "- Consumer tests (orchestrator): Generate contracts defining expected message formats"
echo "- Provider tests (payment-svc): Verify that the service can handle the expected formats"
echo ""
echo "This ensures that the orchestrator and payment-svc have compatible message contracts."
