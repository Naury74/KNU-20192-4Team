
#include <Arduino.h>

#include <Stream.h>

#include <ESP8266WiFi.h>
#include <ESP8266WiFiMulti.h>



//AWS
#include "sha256.h"
#include "Utils.h"


//WEBSockets
#include <Hash.h>
#include <WebSocketsClient.h>

//MQTT PAHO
#include <SPI.h>
#include <IPStack.h>
#include <Countdown.h>
#include <MQTTClient.h>



//AWS MQTT Websocket
#include "Client.h"
#include "AWSWebSocketClient.h"
#include "CircularByteBuffer.h"

extern "C" {
  #include "user_interface.h"
}

//AWS IOT config, change these:
char wifi_ssid[]       = "Note5";
char wifi_password[]   = "00000000";
char aws_endpoint[]    = "a1rgc1vd3jykuy-ats.iot.ap-northeast-2.amazonaws.com";
char aws_key[]         = "AKIA26AIO27UTBS22QMC";
char aws_secret[]      = "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";
char aws_region[]      = "ap-northeast-2";
const char* aws_topic  = "$aws/things/Waterlevel/shadow/update";

int port = 8883; 

//MQTT config
const int maxMQTTpackageSize = 512;
const int maxMQTTMessageHandlers = 1;

ESP8266WiFiMulti WiFiMulti;

AWSWebSocketClient awsWSclient(1000);

IPStack ipstack(awsWSclient);
MQTT::Client<IPStack, Countdown, maxMQTTpackageSize, maxMQTTMessageHandlers> client(ipstack);

//AWS IoT Server Setting
#define THING_NAME "Waterlevel"
char clientID[] = "WaterlevelClient";

char publishPayload[512];
char publishTopic[]   = "$aws/things/" THING_NAME "/shadow/update";
/*char *subscribeTopic[5] = {
  "$aws/things/" THING_NAME "/shadow/update/accepted",
  "$aws/things/" THING_NAME "/shadow/update/rejected",
  "$aws/things/" THING_NAME "/shadow/update/delta",
  "$aws/things/" THING_NAME "/shadow/get/accepted",
  "$aws/things/" THING_NAME "/shadow/get/rejected"
};*/
char *subscribeTopic[2] = {
  "$aws/things/" THING_NAME "/shadow/update/accepted", 
  "$aws/things/" THING_NAME "/shadow/get/accepted", 
};

//of connections
long connection = 0;

//count messages arrived
int arrivedcount = 0;


/*
 * 
 */
void updateWaterlevelState(int desired_Waterlevel_state) {
  printf("update waterlevel_state: %d\r\n", desired_Waterlevel_state);
  
  
  MQTT::Message message;
  char buf[100];  
  sprintf(buf, "{\"state\":{\"reported\":{\"Waterlevel\":%d}},\"clientToken\":\"%s\"}",
    desired_Waterlevel_state,
    clientID
  );
  message.qos = MQTT::QOS0;
  message.retained = false;
  message.dup = false;
  message.payload = (void*)buf;
  message.payloadlen = strlen(buf)+1;
  
  int rc = client.publish(aws_topic, message); 
    
  printf("Publish [%s] %s\r\n", aws_topic, buf);
}
/*
 * 
 */
void messageArrived(MQTT::MessageData& md)
{
  char buf[512];
  char *pch;
  int desired_Waterlevel_state;

  MQTT::Message &message = md.message;

  Serial.print("Message ");
  Serial.print(++arrivedcount);
  Serial.print(" arrived: qos ");
  Serial.print(message.qos);
  Serial.print(", retained ");
  Serial.print(message.retained);
  Serial.print(", dup ");
  Serial.print(message.dup);
  Serial.print(", packetid ");
  Serial.println(message.id);
  Serial.print("Payload ");
  char* msg = new char[message.payloadlen+1]();
  memcpy (msg,message.payload,message.payloadlen);
  Serial.println(msg);
  
  delete msg;
}
/*
 * 
 */
 bool connect () {

    if (client.isConnected ()) {    
        client.disconnect ();
    }  
    //delay is not necessary... it just help us to get a "trustful" heap space value
    delay (1000);
    Serial.print (millis ());
    Serial.print (" - conn: ");
    Serial.print (++connection);
    Serial.print (" - (");
    Serial.print (ESP.getFreeHeap ());
    Serial.println (")");
    
   int rc = ipstack.connect(aws_endpoint, port);
    if (rc != 1)
    {
      Serial.println("error connection to the websocket server");
      return false;
    } else {
      Serial.println("websocket layer connected");
    }

    Serial.println("MQTT connecting");
    MQTTPacket_connectData data = MQTTPacket_connectData_initializer;
    data.MQTTVersion = 4;
    
    data.clientID.cstring = &clientID[0];
    rc = client.connect(data); 
    
    if (rc != 0)
    {
      Serial.print("error connection to MQTT server");
      Serial.println(rc);
      return false;
    }
    Serial.println("MQTT connected");
    return true;
}
/*
 * 
 */
 void subscribe () {
   //subscript to a topic
    
    for (int i=0; i<2; i++) 
    {     
      int rc = client.subscribe(subscribeTopic[i], MQTT::QOS0, messageArrived);
      if (rc != 0) {
        Serial.print("rc from MQTT subscribe is ");
        Serial.println(rc);
        return;
      }
    }
    Serial.println("MQTT subscribed");
}
 

int led = 14;

int Waterlvel = 0;
int Waterlvel_data = 0;

void setup() {
  pinMode(led,OUTPUT);
  wifi_set_sleep_type(NONE_SLEEP_T);
  Serial.begin (115200);
  delay (2000);
 

  WiFiMulti.addAP(wifi_ssid, wifi_password);
  Serial.println ("connecting to wifi");
  while(WiFiMulti.run() != WL_CONNECTED) {
      delay(100);
      Serial.print (".");
  }
  Serial.println ("\nconnected");
  awsWSclient.setAWSRegion(aws_region);
  awsWSclient.setAWSDomain(aws_endpoint);
  awsWSclient.setAWSKeyID(aws_key);
  awsWSclient.setAWSSecretKey(aws_secret);
  awsWSclient.setUseSSL(true);
 
  if (connect ()){
    subscribe ();
  }
  
}


void loop() {
  int Waterlvel_data = analogRead(Waterlvel);
   if (awsWSclient.connected ()) {  
      
      client.yield(10);
  } else {
    //handle reconnection
    if (connect ()){
      subscribe ();      
    }
  }
  updateWaterlevelState(Waterlvel_data);

  if(isnan(Waterlvel_data)){
    Serial.println("Failed to read from Water sensor!");
  }
  else if(Waterlvel_data<700){
    digitalWrite(led,HIGH);
  }
  else{
    digitalWrite(led,LOW);
  }
  waterlevel_show(Waterlvel_data);
  delay(500);
}

void waterlevel_show(int val){
  Serial.print("Water level: ");
  Serial.println(val);
}
