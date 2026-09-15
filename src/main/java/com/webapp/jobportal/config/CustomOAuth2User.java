package com.webapp.jobportal.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

import java.io.Serializable;

public class CustomOAuth2User implements OAuth2User, Serializable {
    private static final long serialVersionUID = 1L;

    private OAuth2User oauth2User;

    public CustomOAuth2User(OAuth2User oauth2User) {
        this.oauth2User = oauth2User;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oauth2User.getAuthorities();
    }

    @Override
    public String getName() {
        String name = oauth2User.getAttribute("name");
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        String givenName = oauth2User.getAttribute("given_name");
        if (givenName != null && !givenName.trim().isEmpty()) {
            return givenName;
        }
        return oauth2User.getAttribute("email");
    }

    public String getEmail() {
        return oauth2User.getAttribute("email");
    }

    public String getGivenName() {
        String givenName = oauth2User.getAttribute("given_name");
        if (givenName != null && !givenName.trim().isEmpty()) {
            return givenName;
        }
        String fullName = oauth2User.getAttribute("name");
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            return parts[0];
        }
        String email = getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "User";
    }

    public String getFamilyName() {
        String familyName = oauth2User.getAttribute("family_name");
        if (familyName != null && !familyName.trim().isEmpty()) {
            return familyName;
        }
        String fullName = oauth2User.getAttribute("name");
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return "";
    }

    public String getPicture() {
        return oauth2User.getAttribute("picture");
    }
}
