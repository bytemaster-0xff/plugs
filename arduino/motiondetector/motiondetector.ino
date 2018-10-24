long next_send;

bool lastMotion;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);

  pinMode(5, INPUT);
}

void loop() {
  // put your main code here, to run repeatedly:
  if(next_send < millis()){
    Serial.println("ping");
    next_send = millis() + 2500;
  }

  if(digitalRead(5)){
    if(!lastMotion) {
      Serial.println("on");
      lastMotion = true;
    }    
  }
  else {
    if(lastMotion) {
      Serial.println("off");
      lastMotion = false;
    }
  }
}
