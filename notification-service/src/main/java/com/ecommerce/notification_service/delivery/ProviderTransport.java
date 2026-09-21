package com.ecommerce.notification_service.delivery;

import com.fasterxml.jackson.databind.*;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import feign.FeignException;

@Service
public class ProviderTransport implements MessageTransport {
    private final Environment env; private final ObjectProvider<JavaMailSender> mail; private final TwilioApi client; private final ObjectMapper json;
    public ProviderTransport(Environment env,ObjectProvider<JavaMailSender> mail,TwilioApi client,ObjectMapper json) {
        this.env=env;this.mail=mail;this.json=json;
        this.client=client;
    }
    private String value(String key) {return env.getProperty(key,"");}
    public boolean enabled(Delivery.Channel channel) {return env.getProperty("messages."+channel.name().toLowerCase()+".enabled",Boolean.class,false);}
    public String send(Delivery d) {
        if(d.channel==Delivery.Channel.EMAIL) {
            JavaMailSender sender=mail.getIfAvailable(); String from=value("messages.email.from");
            if(sender==null || from.isBlank()) throw new Rejected(false);
            SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(d.recipient);m.setSubject(d.subject);m.setText(d.body);
            try {sender.send(m);} catch(MailAuthenticationException | MailParseException ex) {throw new Rejected(false);}
            return "SMTP_ACCEPTED";
        }
        String account=value("messages.twilio.account-sid"), token=value("messages.twilio.auth-token");
        boolean whatsapp=d.channel==Delivery.Channel.WHATSAPP;
        String from=value(whatsapp?"messages.whatsapp.from":"messages.sms.from");
        if(!account.matches("AC[0-9a-fA-F]{32}") || token.isBlank() || from.isBlank() || !d.recipient.matches("\\+[1-9][0-9]{6,14}")) throw new Rejected(false);
        var form=new java.util.LinkedHashMap<String,String>();form.put("From",whatsapp?"whatsapp:"+from:from);form.put("To",whatsapp?"whatsapp:"+d.recipient:d.recipient);
        if(whatsapp) {
            String content=value("messages.whatsapp.content-sid");
            if(!content.matches("HX[0-9a-fA-F]{32}")) throw new Rejected(false);
            form.put("ContentSid",content);
            try {form.put("ContentVariables",json.writeValueAsString(Map.of("1",d.body)));}
            catch(com.fasterxml.jackson.core.JsonProcessingException ex) {throw new Rejected(false);}
        } else form.put("Body",d.body);
        try {
            JsonNode response=client.send(account,"Basic " + HttpHeaders.encodeBasicAuth(account,token,java.nio.charset.StandardCharsets.ISO_8859_1),form);
            if(response==null || !response.path("sid").asText().matches("SM[0-9a-fA-F]{32}")) throw new IllegalStateException("Uncertain provider response");
            return response.path("sid").asText();
        } catch(FeignException ex) {
            if(ex.status() >= 400 && ex.status() < 500) throw new Rejected(ex.status()==429);
            throw ex;
        }
        // Timeouts and 5xx may follow provider acceptance. The worker marks UNKNOWN instead of duplicating a send.
    }
}
