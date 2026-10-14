#!/usr/bin/env bash
set -euo pipefail

# This file is invoked as one command by android-emulator-runner.
# Keep control flow and variables here because its script input executes each line separately.
adb install -r pos-repo/app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.aistudio.powerfuelgateway.yqnw android.permission.RECEIVE_SMS
adb shell pm grant com.aistudio.powerfuelgateway.yqnw android.permission.READ_SMS
adb shell pm grant com.aistudio.powerfuelgateway.yqnw android.permission.POST_NOTIFICATIONS

TEST_TOKEN="ci-integration-token-powerfeul-gateway-2026"
DEVICE_ID="CI_EMULATOR_GATEWAY_01"
SMS_BODY='استلمت حوالة من علي بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي'

adb logcat -c
adb shell am start -n com.aistudio.powerfuelgateway.yqnw/com.example.DebugGatewayIntegrationActivity \
  --es debug_gateway_integration_token "$TEST_TOKEN" \
  --es debug_gateway_integration_device_id "$DEVICE_ID" \
  --es debug_gateway_integration_pos_ip 127.0.0.1 \
  --ei debug_gateway_integration_pos_port 18080

READY=0
for attempt in $(seq 1 60); do
  if adb logcat -d -s GatewayIntegrationFixture:I | grep -q 'GATEWAY_CONFIG_READY'; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" != "1" ]; then
  echo "Gateway integration setup did not report readiness"
  adb logcat -d -s GatewayIntegrationFixture:V
  exit 1
fi

adb emu sms send 0777000111 "$SMS_BODY"
sleep 2
gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.HttpPosGatewayCrossAppEndToEndAndroidTest
