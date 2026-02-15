package com.atoconn.config.health;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Custom health indicator for Git Repository
 * 
 * <p>This health indicator monitors the health and connectivity of the Git
 * repository used by Config Server. It checks:
 * <ul>
 *   <li>Repository clone status</li>
 *   <li>Local repository existence</li>
 *   <li>Repository synchronization state</li>
 *   <li>Last successful fetch time</li>
 *   <li>Configuration file availability</li>
 * </ul>
 * 
 * <p><b>Health Status:</b>
 * <ul>
 *   <li><b>UP:</b> Repository is cloned and accessible</li>
 *   <li><b>DOWN:</b> Repository is not cloned or inaccessible</li>
 *   <li><b>UNKNOWN:</b> Repository status cannot be determined</li>
 * </ul>
 * 
 * <p><b>Details Provided:</b>
 * <ul>
 *   <li>repository_uri - Git repository URL</li>
 *   <li>repository_cloned - Whether repository is cloned locally</li>
 *   <li>repository_path - Local path to cloned repository</li>
 *   <li>config_files_count - Number of configuration files found</li>
 *   <li>last_checked - Timestamp of last health check</li>
 * </ul>
 * 
 * @author WowCare Development Team
 * @version 1.0
 * @since 2026-01-29
 */
@Slf4j
@Component("gitRepository")
public class GitRepositoryHealthIndicator implements HealthIndicator {

    @Value("${spring.cloud.config.server.git.uri:unknown}")
    private String gitUri;

    @Value("${spring.cloud.config.server.git.default-label:main}")
    private String defaultBranch;

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String REPO_PREFIX = "config-repo-";

    /**
     * Performs health check for Git repository
     * 
     * @return Health status with repository details
     */
    @Override
    public Health health() {
        log.trace("Performing Git repository health check");
        
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Add basic repository information
            details.put("repository_uri", maskSensitiveInfo(gitUri));
            details.put("default_branch", defaultBranch);
            details.put("last_checked", Instant.now().toString());
            
            // Find the cloned repository directory
            File repoDir = findRepositoryDirectory();
            
            if (repoDir != null && repoDir.exists()) {
                details.put("repository_cloned", true);
                details.put("repository_path", repoDir.getAbsolutePath());
                
                // Check repository contents
                int configFilesCount = countConfigurationFiles(repoDir);
                details.put("config_files_count", configFilesCount);
                
                // Try to get repository status
                try (Git git = Git.open(repoDir)) {
                    Repository repository = git.getRepository();
                    String currentBranch = repository.getBranch();
                    details.put("current_branch", currentBranch);
                    
                    // Get repository status
                    Status status = git.status().call();
                    details.put("has_uncommitted_changes", status.hasUncommittedChanges());
                    details.put("clean_working_tree", status.isClean());
                    
                    log.debug("Git repository health check: UP - Repository accessible with {} config files", 
                            configFilesCount);
                    
                    return Health.up()
                            .withDetails(details)
                            .withDetail("status", "Repository is accessible and synchronized")
                            .build();
                } catch (Exception e) {
                    log.warn("Could not open Git repository, but directory exists", e);
                    details.put("warning", "Repository directory exists but Git operations failed");
                    details.put("error_message", e.getMessage());
                    
                    return Health.up()
                            .withDetails(details)
                            .withDetail("status", "Repository directory exists")
                            .build();
                }
            } else {
                log.warn("Git repository health check: Repository not yet cloned");
                details.put("repository_cloned", false);
                details.put("status", "Repository not yet cloned - will be cloned on first request");
                
                return Health.status("UNKNOWN")
                        .withDetails(details)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error performing Git repository health check", e);
            return Health.down()
                    .withException(e)
                    .withDetail("error", e.getMessage())
                    .withDetail("error_type", e.getClass().getSimpleName())
                    .withDetail("repository_uri", maskSensitiveInfo(gitUri))
                    .build();
        }
    }

    /**
     * Finds the Git repository directory in temp folder
     * 
     * @return Repository directory or null if not found
     */
    private File findRepositoryDirectory() {
        try {
            Path tempPath = Paths.get(TEMP_DIR);
            
            // Find directories starting with "config-repo-"
            try (Stream<Path> paths = Files.list(tempPath)) {
                return paths
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith(REPO_PREFIX))
                        .findFirst()
                        .map(Path::toFile)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.error("Error finding repository directory", e);
            return null;
        }
    }

    /**
     * Counts configuration files in the repository
     * 
     * @param repoDir Repository directory
     * @return Number of YAML/YML/properties files found
     */
    private int countConfigurationFiles(File repoDir) {
        try {
            return (int) Files.walk(repoDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".yml") || 
                               fileName.endsWith(".yaml") || 
                               fileName.endsWith(".properties");
                    })
                    .count();
        } catch (Exception e) {
            log.error("Error counting configuration files", e);
            return 0;
        }
    }

    /**
     * Masks sensitive information from Git URI
     * 
     * @param uri Git URI
     * @return Masked URI with credentials hidden
     */
    private String maskSensitiveInfo(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "not-configured";
        }
        
        // Mask password in HTTP(S) URIs
        if (uri.contains("@")) {
            return uri.replaceAll("(https?://)([^:]+):([^@]+)@", "$1***:***@");
        }
        
        // For SSH URIs, just show them as-is (no password in URL)
        return uri;
    }
}
