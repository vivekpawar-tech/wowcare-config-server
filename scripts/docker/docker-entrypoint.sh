#!/bin/sh
set -e

# Fix SSH permissions if SSH directory exists
if [ -d "/home/configserver/.ssh" ]; then
    # The mounted .ssh directory needs proper permissions
    chmod 700 /home/configserver/.ssh 2>/dev/null || true
    
    # Set proper permissions for private keys
    if [ -f "/home/configserver/.ssh/id_rsa" ]; then
        chmod 600 /home/configserver/.ssh/id_rsa 2>/dev/null || true
    fi
    
    if [ -f "/home/configserver/.ssh/id_ed25519" ]; then
        chmod 600 /home/configserver/.ssh/id_ed25519 2>/dev/null || true
    fi
    
    # Create known_hosts if it doesn't exist
    if [ ! -f "/home/configserver/.ssh/known_hosts" ]; then
        touch /home/configserver/.ssh/known_hosts
        chmod 644 /home/configserver/.ssh/known_hosts
    fi
    
    # Add GitHub to known_hosts if not already present
    if ! grep -q "github.com" /home/configserver/.ssh/known_hosts 2>/dev/null; then
        ssh-keyscan github.com >> /home/configserver/.ssh/known_hosts 2>/dev/null || true
    fi
fi

# Start SSH agent and add keys
eval $(ssh-agent -s)

# Add SSH keys if they exist
if [ -f "/home/configserver/.ssh/id_rsa" ]; then
    ssh-add /home/configserver/.ssh/id_rsa 2>/dev/null || true
fi

if [ -f "/home/configserver/.ssh/id_ed25519" ]; then
    ssh-add /home/configserver/.ssh/id_ed25519 2>/dev/null || true
fi

# Execute the main application
exec java -jar /app/app.jar "$@"
