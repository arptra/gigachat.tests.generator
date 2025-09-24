GIGACHAT_API_BASE="https://gigachat.devices.sberbank.ru/" \
GIGACHAT_AUTH_URL="https://ngw.devices.sberbank.ru:9443/api/v2/oauth" \
GIGACHAT_API_KEY="" \
GIGACHAT_MODEL="GigaChat-2-Max" \
GIGACHAT_AGENT_LOG_LEVEL="FINE" \
java -Djavax.net.ssl.trustStore="gigachat-truststore.p12" \
-Djavax.net.ssl.trustStorePassword=changeit \
-Djavax.net.ssl.trustStoreType=PKCS12 \
 -jar build/libs/gigachat-tests-generator-1.0-SNAPSHOT.jar \
  --project "$(pwd)" \
  --class com.acme.discount.DiscountService
