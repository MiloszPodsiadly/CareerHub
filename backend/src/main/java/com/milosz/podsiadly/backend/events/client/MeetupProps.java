package com.milosz.podsiadly.backend.events.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix="events.meetup")
public record MeetupProps(List<String> groups) {}
