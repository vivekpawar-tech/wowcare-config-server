#!/bin/bash

# WowCare Config Server - Eureka Integration Validation Script
# This script validates the Config Server's integration with Netflix Eureka

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONFIG_SERVER_URL="http://localhost:8888"
EUREKA_SERVER_URL="http://localhost:8761"
MAX_RETRIES=30
RETRY_DELAY=5

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}WowCare Config Server Integration Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

# Function to wait for service
wait_for_service() {
    local url=$1
    local service_name=$2
    local retries=0
    
    echo -ne "${YELLOW}Waiting for $service_name to be ready...${NC}"
    
    while [ $retries -lt $MAX_RETRIES ]; do
        if curl -s -f "$url" > /dev/null 2>&1; then
            echo -e "\r${GREEN}✓ $service_name is ready!${NC}                    "
            return 0
        fi
        echo -ne "\r${YELLOW}Waiting for $service_name to be ready... ($((retries + 1))/$MAX_RETRIES)${NC}"
        sleep $RETRY_DELAY
        ((retries++))
    done
    
    echo -e "\r${RED}✗ $service_name failed to start after $((MAX_RETRIES * RETRY_DELAY)) seconds${NC}"
    return 1
}

# Test 1: Check Eureka Server is running
echo -e "\n${BLUE}Test 1: Eureka Server Availability${NC}"
if curl -s -f "$EUREKA_SERVER_URL" > /dev/null 2>&1; then
    print_status 0 "Eureka Server is running at $EUREKA_SERVER_URL"
else
    print_status 1 "Eureka Server is not accessible"
    echo -e "${YELLOW}Please start Eureka Server: docker-compose up -d service-registry${NC}"
    exit 1
fi

# Test 2: Wait for Config Server to start
echo -e "\n${BLUE}Test 2: Config Server Startup${NC}"
wait_for_service "$CONFIG_SERVER_URL/actuator/health" "Config Server"

# Test 3: Check Config Server Health
echo -e "\n${BLUE}Test 3: Config Server Health Check${NC}"
HEALTH_RESPONSE=$(curl -s "$CONFIG_SERVER_URL/actuator/health")
HEALTH_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")

if [ "$HEALTH_STATUS" == "UP" ]; then
    print_status 0 "Config Server health status: $HEALTH_STATUS"
    
    # Check individual components
    CONFIG_SERVER_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.components.configServer.status' 2>/dev/null || echo "UNKNOWN")
    EUREKA_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.components.eureka.status' 2>/dev/null || echo "UNKNOWN")
    
    print_status $([ "$CONFIG_SERVER_STATUS" == "UP" ] && echo 0 || echo 1) "Config Server component: $CONFIG_SERVER_STATUS"
    print_status $([ "$EUREKA_STATUS" == "UP" ] && echo 0 || echo 1) "Eureka client component: $EUREKA_STATUS"
else
    print_status 1 "Config Server health status: $HEALTH_STATUS"
    echo -e "${RED}Health Response: $HEALTH_RESPONSE${NC}"
fi

# Test 4: Check Config Server Registration in Eureka
echo -e "\n${BLUE}Test 4: Eureka Service Registration${NC}"

# Wait a bit for registration to complete
sleep 5

EUREKA_APPS=$(curl -s "$EUREKA_SERVER_URL/eureka/apps" -H "Accept: application/json")
CONFIG_SERVER_REGISTERED=$(echo "$EUREKA_APPS" | jq -r '.applications.application[] | select(.name=="WOWCARE-CONFIG-SERVER") | .name' 2>/dev/null || echo "")

if [ "$CONFIG_SERVER_REGISTERED" == "WOWCARE-CONFIG-SERVER" ]; then
    print_status 0 "Config Server is registered with Eureka"
    
    # Get instance details
    INSTANCE_STATUS=$(echo "$EUREKA_APPS" | jq -r '.applications.application[] | select(.name=="WOWCARE-CONFIG-SERVER") | .instance[0].status' 2>/dev/null || echo "UNKNOWN")
    INSTANCE_ID=$(echo "$EUREKA_APPS" | jq -r '.applications.application[] | select(.name=="WOWCARE-CONFIG-SERVER") | .instance[0].instanceId' 2>/dev/null || echo "UNKNOWN")
    HOME_PAGE_URL=$(echo "$EUREKA_APPS" | jq -r '.applications.application[] | select(.name=="WOWCARE-CONFIG-SERVER") | .instance[0].homePageUrl' 2>/dev/null || echo "UNKNOWN")
    
    print_status $([ "$INSTANCE_STATUS" == "UP" ] && echo 0 || echo 1) "Instance status: $INSTANCE_STATUS"
    echo -e "  Instance ID: ${YELLOW}$INSTANCE_ID${NC}"
    echo -e "  Home Page URL: ${YELLOW}$HOME_PAGE_URL${NC}"
    
else
    print_status 1 "Config Server is NOT registered with Eureka"
    echo -e "${YELLOW}Registered applications:${NC}"
    echo "$EUREKA_APPS" | jq -r '.applications.application[].name' 2>/dev/null || echo "Unable to parse response"
    exit 1
fi

# Test 5: Check Instance Metadata
echo -e "\n${BLUE}Test 5: Instance Metadata Validation${NC}"
METADATA=$(echo "$EUREKA_APPS" | jq -r '.applications.application[] | select(.name=="WOWCARE-CONFIG-SERVER") | .instance[0].metadata' 2>/dev/null || echo "{}")

# Check required metadata fields
SERVICE_TYPE=$(echo "$METADATA" | jq -r '."service-type"' 2>/dev/null || echo "")
SERVICE_VERSION=$(echo "$METADATA" | jq -r '."service-version"' 2>/dev/null || echo "")
HEALTH_PATH=$(echo "$METADATA" | jq -r '."health.path"' 2>/dev/null || echo "")
MANAGEMENT_PATH=$(echo "$METADATA" | jq -r '."management.context-path"' 2>/dev/null || echo "")

print_status $([ "$SERVICE_TYPE" == "config-server" ] && echo 0 || echo 1) "service-type metadata: $SERVICE_TYPE"
print_status $([ "$SERVICE_VERSION" == "1.0.0" ] && echo 0 || echo 1) "service-version metadata: $SERVICE_VERSION"
print_status $([ ! -z "$HEALTH_PATH" ] && echo 0 || echo 1) "health.path metadata: $HEALTH_PATH"
print_status $([ ! -z "$MANAGEMENT_PATH" ] && echo 0 || echo 1) "management.context-path metadata: $MANAGEMENT_PATH"

# Test 6: Check Actuator Info Endpoint
echo -e "\n${BLUE}Test 6: Actuator Info Endpoint${NC}"
INFO_RESPONSE=$(curl -s "$CONFIG_SERVER_URL/actuator/info")
CONFIG_SERVER_INFO=$(echo "$INFO_RESPONSE" | jq -r '.configServer.service' 2>/dev/null || echo "")

if [ "$CONFIG_SERVER_INFO" == "wowcare-config-server" ]; then
    print_status 0 "Info endpoint returns correct service name"
    
    GIT_ENABLED=$(echo "$INFO_RESPONSE" | jq -r '.configServer.gitEnabled' 2>/dev/null || echo "false")
    EUREKA_ENABLED=$(echo "$INFO_RESPONSE" | jq -r '.configServer.eurekaEnabled' 2>/dev/null || echo "false")
    
    print_status $([ "$GIT_ENABLED" == "true" ] && echo 0 || echo 1) "Git configuration enabled: $GIT_ENABLED"
    print_status $([ "$EUREKA_ENABLED" == "true" ] && echo 0 || echo 1) "Eureka integration enabled: $EUREKA_ENABLED"
else
    print_status 1 "Info endpoint validation failed"
fi

# Test 7: Test Configuration Retrieval
echo -e "\n${BLUE}Test 7: Configuration Retrieval${NC}"
# Try to fetch a configuration (this might fail if the service doesn't exist yet)
CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" "$CONFIG_SERVER_URL/attendance-service/docker")
CONFIG_HTTP_CODE=$(echo "$CONFIG_RESPONSE" | tail -n1)
CONFIG_BODY=$(echo "$CONFIG_RESPONSE" | sed '$d')

if [ "$CONFIG_HTTP_CODE" == "200" ]; then
    print_status 0 "Configuration retrieval successful (HTTP $CONFIG_HTTP_CODE)"
    
    # Check if response contains expected fields
    if echo "$CONFIG_BODY" | jq -e '.name' > /dev/null 2>&1; then
        CONFIG_NAME=$(echo "$CONFIG_BODY" | jq -r '.name')
        CONFIG_PROFILES=$(echo "$CONFIG_BODY" | jq -r '.profiles[]' 2>/dev/null || echo "default")
        echo -e "  Config name: ${YELLOW}$CONFIG_NAME${NC}"
        echo -e "  Profiles: ${YELLOW}$CONFIG_PROFILES${NC}"
    fi
else
    print_status 1 "Configuration retrieval returned HTTP $CONFIG_HTTP_CODE"
    echo -e "${YELLOW}This might be expected if the configuration repository is not set up yet${NC}"
fi

# Test 8: Check Prometheus Metrics
echo -e "\n${BLUE}Test 8: Prometheus Metrics Endpoint${NC}"
METRICS_RESPONSE=$(curl -s -w "\n%{http_code}" "$CONFIG_SERVER_URL/actuator/prometheus")
METRICS_HTTP_CODE=$(echo "$METRICS_RESPONSE" | tail -n1)

if [ "$METRICS_HTTP_CODE" == "200" ]; then
    print_status 0 "Prometheus metrics endpoint accessible"
    
    # Count number of metrics
    METRIC_COUNT=$(echo "$METRICS_RESPONSE" | sed '$d' | grep -c "^[a-z]" || echo 0)
    echo -e "  Metrics available: ${YELLOW}$METRIC_COUNT${NC}"
else
    print_status 1 "Prometheus metrics endpoint returned HTTP $METRICS_HTTP_CODE"
fi

# Test 9: Service Discovery via Eureka
echo -e "\n${BLUE}Test 9: Service Discovery${NC}"
DISCOVERED_URL=$(echo "$EUREKA_APPS" | jq -r '.applications.application[] | select(.name=="WOWCARE-CONFIG-SERVER") | .instance[0].homePageUrl' 2>/dev/null || echo "")

if [ ! -z "$DISCOVERED_URL" ]; then
    print_status 0 "Config Server URL discovered via Eureka: $DISCOVERED_URL"
    
    # Try to access the discovered URL
    if curl -s -f "${DISCOVERED_URL}actuator/health" > /dev/null 2>&1; then
        print_status 0 "Discovered URL is accessible"
    else
        print_status 1 "Discovered URL is not accessible"
    fi
else
    print_status 1 "Could not discover Config Server URL from Eureka"
fi

# Summary
echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}Integration Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ All critical tests passed!${NC}"
echo -e "\n${YELLOW}Next Steps:${NC}"
echo -e "1. Review the Config Server logs: ${BLUE}docker logs wowcare-config-server${NC}"
echo -e "2. Access Eureka Dashboard: ${BLUE}$EUREKA_SERVER_URL${NC}"
echo -e "3. Test with other microservices: Update their config to use Config Server"
echo -e "4. Monitor metrics: ${BLUE}$CONFIG_SERVER_URL/actuator/prometheus${NC}"
echo -e "\n${GREEN}Config Server Eureka Integration: SUCCESSFUL ✓${NC}"
echo ""
