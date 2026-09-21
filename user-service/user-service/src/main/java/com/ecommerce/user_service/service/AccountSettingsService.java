package com.ecommerce.user_service.service;

import com.ecommerce.user_service.dto.requests.*;
import com.ecommerce.user_service.entity.*;
import com.ecommerce.user_service.exception.ApiException;
import com.ecommerce.user_service.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Transactional
public class AccountSettingsService {
    private final UserRepository users;
    private final AddressRepository addresses;
    private final UserPreferencesRepository prefs;
    private final EventPublisher events;

    private User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ApiException("User not found"));
    }

    public List<Address> addresses(String email) {
        return addresses.findByUserOrderByDefaultAddressDescIdDesc(user(email));
    }

    public Address add(String email, AddressRequest r) {
        User u = user(email);
        Address a = new Address();
        copy(a, r);
        a.setUser(u);
        if (addresses.findByUserOrderByDefaultAddressDescIdDesc(u).isEmpty()) {
            a.setDefaultAddress(true);
        }
        addresses.save(a);
        events.publish("AddressAddedEvent", u.getId(), u.getEmail());
        return a;
    }

    public Address update(String email, Long id, AddressRequest r) {
        Address a = owned(email, id);
        copy(a, r);
        return a;
    }

    public void delete(String email, Long id) {
        addresses.delete(owned(email, id));
    }

    public Address makeDefault(String email, Long id) {
        User u = user(email);
        Address a = owned(email, id);
        if (a.isDefaultAddress()) {
            return a;
        }
        addresses.clearDefault(u);
        a.setDefaultAddress(true);
        return a;
    }

    public UserPreferences preferences(String email) {
        User u = user(email);
        return prefs.findByUser(u).orElseGet(() -> {
            UserPreferences p = new UserPreferences();
            p.setUser(u);
            return prefs.save(p);
        });
    }

    public UserPreferences updatePreferences(String email, PreferencesRequest r) {
        UserPreferences p = preferences(email);
        if (r.getLanguage() != null) p.setLanguage(r.getLanguage());
        if (r.getCurrency() != null) p.setCurrency(r.getCurrency());
        if (r.getDarkMode() != null) p.setDarkMode(r.getDarkMode());
        if (r.getEmailNotifications() != null) p.setEmailNotifications(r.getEmailNotifications());
        if (r.getSmsNotifications() != null) p.setSmsNotifications(r.getSmsNotifications());
        if (r.getWhatsAppNotifications() != null) p.setWhatsAppNotifications(r.getWhatsAppNotifications());
        if (r.getPushNotifications() != null) p.setPushNotifications(r.getPushNotifications());
        if (r.getCommunicationPreference() != null) p.setCommunicationPreference(r.getCommunicationPreference());
        return p;
    }

    private Address owned(String email, Long id) {
        User u = user(email);
        Address a = addresses.findById(id).orElseThrow(() -> new ApiException("Address not found"));
        if (!a.getUser().getId().equals(u.getId())) throw new ApiException("Address not found");
        return a;
    }

    private void copy(Address a, AddressRequest r) {
        a.setLine1(r.getLine1());
        a.setLine2(r.getLine2());
        a.setCity(r.getCity());
        a.setState(r.getState());
        a.setPostalCode(r.getPostalCode());
        a.setCountry(r.getCountry());
    }

    public AccountSettingsService(final UserRepository users, final AddressRepository addresses, final UserPreferencesRepository prefs, final EventPublisher events) {
        this.users = users;
        this.addresses = addresses;
        this.prefs = prefs;
        this.events = events;
    }
}
