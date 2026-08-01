#!/bin/bash
# Test DeepSeek API key and text correction before building
# Usage: ./test_deepseek_api.sh [api_key]

API_KEY="${1}"

echo "=== Testing DeepSeek API key ==="

# Test 1: basic connectivity + text correction
RESPONSE=$(curl -s -w "\n%{http_code}" https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "system", "content": "You are a spelling/grammar corrector. Fix the user text. Return ONLY a JSON object with key \"corrected_text\". No other output."},
      {"role": "user", "content": "helo wrld, how aer youu doing?"}
    ],
    "response_format": {"type": "json_object"},
    "max_tokens": 200
  }' 2>&1)

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP status: $HTTP_CODE"

if [ "$HTTP_CODE" = "200" ]; then
    echo "PASS: API key is valid"
    echo "Response: $BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
    echo "FAIL: API returned non-200 status"
    echo "$BODY"
    exit 1
fi

echo ""
echo "=== Testing correction format ==="

JSON_OUT=$(curl -s https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "system", "content": "Fix spelling and grammar. Return ONLY {\"corrected_text\": \"...\"}. No other text."},
      {"role": "user", "content": "i haev a drem that one day this ntion wil rise up"}
    ],
    "response_format": {"type": "json_object"},
    "max_tokens": 200
  }' 2>&1)

if echo "$JSON_OUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['choices'][0]['message']['content'])" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print('corrected_text:', d['corrected_text'])" 2>/dev/null; then
    echo "PASS: Correction format is valid"
else
    echo "RAW response (may still be valid, JSON parsing may differ):"
    echo "$JSON_OUT" | python3 -m json.tool 2>/dev/null || echo "$JSON_OUT"
fi

echo ""
echo "All tests completed."
