package com.baz.searchapi.model.entity;

import jakarta.persistence.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "client_social_links", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "link")
    private List<String> socialLinks;

    public Client() {}

    public Client(UUID id, String firstName, String lastName, String email, String description, List<String> socialLinks) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.description = description;
        this.socialLinks = socialLinks;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getSocialLinks() { return socialLinks; }
    public void setSocialLinks(List<String> socialLinks) { this.socialLinks = socialLinks; }
}
