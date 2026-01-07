#!/bin/bash

# Concurrency Test Script for Internal Transfers System
# This script demonstrates that the locking mechanism prevents race conditions

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=============================================="
echo "  Concurrency Test for Internal Transfers"
echo "=============================================="
echo ""

# Cleanup: Reset accounts
echo -e "${YELLOW}Step 1: Setting up test accounts...${NC}"

# Delete existing accounts by creating fresh ones (if needed, restart containers)
# Create Account 100 with $1000
curl -s -X POST "$BASE_URL/accounts" \
  -H "Content-Type: application/json" \
  -d '{"account_id": 100, "initial_balance": "1000"}' > /dev/null 2>&1

# Create Account 200 with $0
curl -s -X POST "$BASE_URL/accounts" \
  -H "Content-Type: application/json" \
  -d '{"account_id": 200, "initial_balance": "0"}' > /dev/null 2>&1

echo "  Account 100: $(curl -s "$BASE_URL/accounts/100" | grep -o '"balance":"[^"]*"' | cut -d'"' -f4)"
echo "  Account 200: $(curl -s "$BASE_URL/accounts/200" | grep -o '"balance":"[^"]*"' | cut -d'"' -f4)"
echo ""

echo -e "${YELLOW}Step 2: Launching 10 concurrent transfer requests of \$100 each...${NC}"
echo "  (Account 100 → Account 200)"
echo ""

# Launch 10 concurrent transfers
for i in {1..10}; do
  curl -s -X POST "$BASE_URL/transactions" \
    -H "Content-Type: application/json" \
    -d '{"source_account_id": 100, "destination_account_id": 200, "amount": "100"}' \
    -w "Transfer $i: HTTP %{http_code}\n" -o /dev/null &
done

# Wait for all transfers to complete
wait

echo ""
echo -e "${YELLOW}Step 3: Checking final balances...${NC}"

BALANCE_100=$(curl -s "$BASE_URL/accounts/100" | grep -o '"balance":"[^"]*"' | cut -d'"' -f4)
BALANCE_200=$(curl -s "$BASE_URL/accounts/200" | grep -o '"balance":"[^"]*"' | cut -d'"' -f4)

echo "  Account 100: \$$BALANCE_100"
echo "  Account 200: \$$BALANCE_200"
echo ""

# Verify total money is still $1000
TOTAL=$(echo "$BALANCE_100 + $BALANCE_200" | bc)

echo "=============================================="
if [ "$TOTAL" == "1000" ]; then
  echo -e "${GREEN}✅ TEST PASSED!${NC}"
  echo "  Total money preserved: \$$TOTAL"
  echo "  No data corruption occurred."
else
  echo -e "${RED}❌ TEST FAILED!${NC}"
  echo "  Total money: \$$TOTAL (expected \$1000)"
  echo "  Data corruption detected!"
fi
echo "=============================================="
echo ""

echo -e "${YELLOW}Step 4: Testing insufficient balance scenario...${NC}"
echo "  Attempting to transfer \$500 from Account 100 (current balance: \$$BALANCE_100)"
echo ""

RESPONSE=$(curl -s -X POST "$BASE_URL/transactions" \
  -H "Content-Type: application/json" \
  -d '{"source_account_id": 100, "destination_account_id": 200, "amount": "500"}')

if echo "$RESPONSE" | grep -q "INSUFFICIENT_BALANCE"; then
  echo -e "${GREEN}✅ Insufficient balance correctly rejected!${NC}"
  echo "  Response: $(echo $RESPONSE | grep -o '"message":"[^"]*"' | cut -d'"' -f4)"
else
  echo -e "${RED}❌ Should have been rejected!${NC}"
fi

echo ""
echo "=============================================="
echo "  Test Complete"
echo "=============================================="
