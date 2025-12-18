package com.kalvium.model;

public class AuthConfig {
    private String authSessionId;
    private String keycloakIdentity;
    private String keycloakSession;
    private String tasksCompleted;
    private String challenges;
    private String blockers;

    public AuthConfig() {}

    public String getAuthSessionId() { return authSessionId; }
    public void setAuthSessionId(String authSessionId) { this.authSessionId = authSessionId; }

    public String getKeycloakIdentity() { return keycloakIdentity; }
    public void setKeycloakIdentity(String keycloakIdentity) { this.keycloakIdentity = keycloakIdentity; }

    public String getKeycloakSession() { return keycloakSession; }
    public void setKeycloakSession(String keycloakSession) { this.keycloakSession = keycloakSession; }

    public String getTasksCompleted() { return tasksCompleted; }
    public void setTasksCompleted(String tasksCompleted) { this.tasksCompleted = tasksCompleted; }

    public String getChallenges() { return challenges; }
    public void setChallenges(String challenges) { this.challenges = challenges; }

    public String getBlockers() { return blockers; }
    public void setBlockers(String blockers) { this.blockers = blockers; }
}
