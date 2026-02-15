#!/bin/bash

###############################################################################
# Configuration Refresh Script for WowCare Microservices
# 
# This script triggers configuration refresh across all microservices
# after updating configuration in the Config Server Git repository.
#
# Usage: ./refresh-all-services.sh [environment]
# Example: ./refresh-all-services.sh local
#          ./refresh-all-services.sh dev
#
# Prerequisites:
# - Config Server must be running
# - Microservices must be running with CONFIG_SERVER_ENABLED=true
# - Configuration changes must be committed to Git repository
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT=${1:-local}
CONFIG_SERVER_URL="http://localhost:8888"
TIMEOUT=5

# Service definitions: name, host, port, context-path
declare -A SERVICES=(
    ["user-service"]="localhost:8081:"
    ["organization-service"]="localhost:8081:/wowcare-school-ms"
    ["attendance-service"]="localhost:8083:/attendance"
)

###############################################################################
# Functions
###############################################################################

print_header() {
    echo ""
    echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}  ${GREEN}WowCare Configuration Refresh Script${NC}                   ${BLUE}║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_section() {
    echo ""
    echo -e "${YELLOW}▶ $1${NC}"
    echo "────────────────────────────────────────────────────────────────"
}

check_config_server() {
    print_section "Checking Config Server Status"
    
    if curl -sf "${CONFIG_SERVER_URL}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Config Server is running at ${CONFIG_SERVER_URL}"
        return 0
    else
        echo -e "${RED}✗${NC} Config Server is not running at ${CONFIG_SERVER_URL}"
        echo "Please start Config Server before refreshing services."
        return 1
    fi
}

verify_git_changes() {
    print_section "Verifying Git Repository Status"
    
    cd "$(dirname "$0")/../wowcare-configuration-service" 2>/dev/null || {
        echo -e "${YELLOW}!${NC} Could not find configuration repository"
        echo "Skipping Git verification..."
        return 0
    }
    
    # Check if there are uncommitted changes
    if [[ -n $(git status --porcelain) ]]; then
        echo -e "${YELLOW}!${NC} Warning: You have uncommitted changes in the configuration repository:"
        git status --short
        echo ""
        read -p "Do you want to commit these changes before refreshing? (y/n): " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            read -p "Enter commit message: " commit_message
            git add .
            git commit -m "${commit_message}"
            echo -e "${GREEN}✓${NC} Changes committed successfully"
        else
            echo -e "${YELLOW}!${NC} Proceeding without committing changes..."
        fi
    else
        echo -e "${GREEN}✓${NC} No uncommitted changes in configuration repository"
    fi
    
    # Show last commit
    echo ""
    echo "Last configuration change:"
    git log -1 --pretty=format:"  Commit: %h%n  Author: %an%n  Date:   %ar%n  Message: %s%n" --color=always
}

test_config_server_service() {
    local service_name=$1
    local profile=$2
    
    echo -n "  Testing ${service_name}/${profile} configuration... "
    
    if curl -sf "${CONFIG_SERVER_URL}/${service_name}/${profile}" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC}"
        return 0
    else
        echo -e "${RED}✗${NC}"
        return 1
    fi
}

refresh_service() {
    local service_name=$1
    local service_config=$2
    
    # Parse service configuration
    IFS=':' read -r host port context_path <<< "${service_config}"
    
    local refresh_url="http://${host}:${port}${context_path}/actuator/refresh"
    
    echo -n "  Refreshing ${service_name}... "
    
    # Trigger refresh
    response=$(curl -sf -X POST "${refresh_url}" -H "Content-Type: application/json" -m ${TIMEOUT} 2>&1) || {
        echo -e "${RED}✗ Failed${NC}"
        echo "    Error: Could not connect to service at ${refresh_url}"
        return 1
    }
    
    # Check if refresh was successful
    if [[ $? -eq 0 ]]; then
        # Count refreshed properties
        property_count=$(echo "${response}" | jq '. | length' 2>/dev/null || echo "0")
        
        if [[ ${property_count} -gt 0 ]]; then
            echo -e "${GREEN}✓ Success${NC} (${property_count} properties refreshed)"
            
            # Show refreshed properties if requested
            if [[ "${VERBOSE}" == "true" ]]; then
                echo "    Refreshed properties:"
                echo "${response}" | jq -r '.[]' | sed 's/^/      - /'
            fi
        else
            echo -e "${YELLOW}⚠ No changes${NC} (configuration unchanged)"
        fi
        return 0
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

verify_service_health() {
    local service_name=$1
    local service_config=$2
    
    # Parse service configuration
    IFS=':' read -r host port context_path <<< "${service_config}"
    
    local health_url="http://${host}:${port}${context_path}/actuator/health"
    
    echo -n "  Checking ${service_name} health... "
    
    health_status=$(curl -sf "${health_url}" -m ${TIMEOUT} 2>&1 | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    
    if [[ "${health_status}" == "UP" ]]; then
        echo -e "${GREEN}✓ UP${NC}"
        return 0
    else
        echo -e "${RED}✗ ${health_status}${NC}"
        return 1
    fi
}

###############################################################################
# Main Script
###############################################################################

main() {
    print_header
    
    echo "Environment: ${ENVIRONMENT}"
    echo "Timeout: ${TIMEOUT}s"
    echo ""
    
    # Check if Config Server is running
    check_config_server || exit 1
    
    # Verify Git repository status
    verify_git_changes
    
    # Test Config Server endpoints
    print_section "Verifying Config Server Configuration"
    
    success_count=0
    for service_name in "${!SERVICES[@]}"; do
        if test_config_server_service "${service_name}" "${ENVIRONMENT}"; then
            ((success_count++))
        fi
    done
    
    if [[ ${success_count} -eq 0 ]]; then
        echo ""
        echo -e "${RED}Error: Config Server is not serving configuration for any services${NC}"
        echo "Please verify:"
        echo "  1. Configuration files exist in wowcare-configuration-service repository"
        echo "  2. Service names match configuration file names"
        echo "  3. Profile '${ENVIRONMENT}' exists for each service"
        exit 1
    fi
    
    # Refresh services
    print_section "Refreshing Microservices Configuration"
    
    refresh_success=0
    refresh_failed=0
    refresh_nochange=0
    
    for service_name in "${!SERVICES[@]}"; do
        if refresh_service "${service_name}" "${SERVICES[${service_name}]}"; then
            ((refresh_success++))
        else
            ((refresh_failed++))
        fi
    done
    
    # Verify service health
    print_section "Verifying Service Health"
    
    health_up=0
    health_down=0
    
    for service_name in "${!SERVICES[@]}"; do
        if verify_service_health "${service_name}" "${SERVICES[${service_name}]}"; then
            ((health_up++))
        else
            ((health_down++))
        fi
    done
    
    # Print summary
    print_section "Refresh Summary"
    
    echo ""
    echo "Configuration Server:"
    echo "  Status: ${GREEN}Running${NC}"
    echo "  URL: ${CONFIG_SERVER_URL}"
    echo ""
    echo "Services Refreshed:"
    echo "  ${GREEN}✓ Success:${NC} ${refresh_success}"
    if [[ ${refresh_failed} -gt 0 ]]; then
        echo "  ${RED}✗ Failed:${NC} ${refresh_failed}"
    fi
    echo ""
    echo "Service Health:"
    echo "  ${GREEN}✓ Healthy:${NC} ${health_up}"
    if [[ ${health_down} -gt 0 ]]; then
        echo "  ${RED}✗ Unhealthy:${NC} ${health_down}"
    fi
    echo ""
    
    if [[ ${refresh_failed} -eq 0 && ${health_down} -eq 0 ]]; then
        echo -e "${GREEN}✓ Configuration refresh completed successfully!${NC}"
        echo ""
        return 0
    else
        echo -e "${RED}✗ Configuration refresh completed with errors${NC}"
        echo ""
        echo "Troubleshooting:"
        echo "  1. Check if all services are running"
        echo "  2. Verify actuator/refresh endpoint is exposed"
        echo "  3. Review service logs for errors"
        echo "  4. Ensure CONFIG_SERVER_ENABLED=true"
        echo ""
        return 1
    fi
}

# Parse command line arguments
VERBOSE=false
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [environment] [options]"
            echo ""
            echo "Arguments:"
            echo "  environment    Configuration profile (default: local)"
            echo ""
            echo "Options:"
            echo "  -v, --verbose  Show detailed output including refreshed properties"
            echo "  -h, --help     Display this help message"
            echo ""
            echo "Examples:"
            echo "  $0 local       Refresh local environment configuration"
            echo "  $0 dev -v      Refresh dev environment with verbose output"
            exit 0
            ;;
        *)
            ENVIRONMENT=$1
            shift
            ;;
    esac
done

# Run main function
main
exit $?
