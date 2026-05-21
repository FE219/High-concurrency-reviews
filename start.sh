#!/bin/bash
# Server startup script - uses application-prod.yml for configuration
nohup java -jar /opt/hmdp/AI-Agent-hm-dianping-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  > /opt/hmdp/app.log 2>&1 &

echo "应用已启动，PID: $!"
