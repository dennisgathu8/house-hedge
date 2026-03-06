#!/bin/bash

PORT=3000
URL="http://localhost:$PORT/api/bankroll"
KEY="house-edge-dev-key"

echo "=== Security Middleware Test ==="
echo ""

# 1. Test Without API Key
echo "[Test 1] Requesting without API Key..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" $URL)
if [ "$RESPONSE" -eq 401 ]; then
  echo "✅ SUCCESS: Blocked unauthorized request (401)"
else
  echo "❌ FAIL: Expected 401, got $RESPONSE"
fi
echo ""

# 2. Test With API Key
echo "[Test 2] Requesting with valid API Key..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: $KEY" $URL)
if [ "$RESPONSE" -eq 200 ]; then
  echo "✅ SUCCESS: Allowed authorized request (200)"
else
  echo "❌ FAIL: Expected 200, got $RESPONSE"
fi
echo ""

# 3. Test Rate Limiting (60 requests per minute)
echo "[Test 3] Testing Rate Limiting (Sending 65 requests)..."
SUCCESS_COUNT=0
BLOCKED_COUNT=0

for i in {1..65}; do
  RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: $KEY" $URL)
  if [ "$RESPONSE" -eq 200 ]; then
    ((SUCCESS_COUNT++))
  elif [ "$RESPONSE" -eq 429 ]; then
    ((BLOCKED_COUNT++))
  fi
done

echo "Successful requests (200): $SUCCESS_COUNT"
echo "Blocked requests (429): $BLOCKED_COUNT"

if [ "$SUCCESS_COUNT" -le 60 ] && [ "$BLOCKED_COUNT" -ge 5 ]; then
  echo "✅ SUCCESS: Rate limiting working as expected"
else
  echo "❌ FAIL: Rate limiting did not trigger correctly"
fi
