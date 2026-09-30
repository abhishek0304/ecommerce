package com.ecommerce.order_service.shipping;

import com.ecommerce.order_service.model.PurchaseOrder;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;

@Component
public class CarrierProviders implements ShippingProvider {
    private final ShiprocketApi shiprocket;
    private final DelhiveryApi delhivery;
    private final ObjectMapper json;
    private final Environment env;
    public CarrierProviders(ShiprocketApi shiprocket,DelhiveryApi delhivery,ObjectMapper json,Environment env) {
        this.shiprocket=shiprocket; this.delhivery=delhivery; this.json=json; this.env=env;
    }
    private String property(String key) { return env.getProperty("shipping."+key,""); }
    private String required(String key) {
        String value=property(key);
        if(value.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure shipping."+key);
        return value;
    }
    private void gate(Provider provider,Mode mode) {
        if(mode==Mode.MOCK) return;
        if(mode==Mode.LIVE && !env.getProperty("shipping.live-enabled",Boolean.class,false))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Live shipping is disabled");
        if(provider==Provider.SHIPROCKET) {
            if(mode!=Mode.LIVE) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Shiprocket has no configured staging integration; use MOCK");
            String base=env.getProperty("shipping.shiprocket.base-url","https://apiv2.shiprocket.in/v1/external");
            if(!base.equals("https://apiv2.shiprocket.in/v1/external")) throw new IllegalStateException("Unexpected Shiprocket origin");
            required("shiprocket.token");
        } else {
            String expected=mode==Mode.STAGING?"https://staging-express.delhivery.com":"https://track.delhivery.com";
            if(!expected.equals(env.getProperty("shipping.delhivery.base-url","https://staging-express.delhivery.com")))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Delhivery origin does not match shipping mode");
            required("delhivery.token");
        }
    }
    public void validate(Provider provider,Mode mode,PurchaseOrder order,Booking request) {
        gate(provider,mode);
        JsonNode address=read(order.addressJson);
        for(String field:List.of("line1","city","state","postalCode","country")) {
            if(address.path(field).asText().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Order address missing "+field);
        }
        if(!Set.of("IN","India").contains(address.path("country").asText()) || !address.path("postalCode").asText().matches("[1-9][0-9]{5}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This integration supports Indian domestic addresses only");
        if(mode==Mode.MOCK) return;
        required(provider==Provider.SHIPROCKET?"shiprocket.pickup-location":"delhivery.pickup-location");
        for(String field:List.of("name","phone","email","address","city","state","pincode")) required("warehouse."+field);
        if(request.hsn()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"HSN is required for provider booking");
        if(order.total.compareTo(new BigDecimal("50000"))>0 && request.ewaybill()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide the shipment e-waybill number");
        if(provider==Provider.DELHIVERY) { required("delhivery.client-name"); required("warehouse.gstin"); }
        if(provider==Provider.SHIPROCKET && "APPROVED".equals(order.returnStatus)) required("shiprocket.channel-id");
    }
    public Created create(Shipment shipment,PurchaseOrder order,Booking request) {
        gate(shipment.provider,shipment.mode);
        if(shipment.mode==Mode.MOCK) return new Created("MOCK-"+shipment.id,"MOCK-"+shipment.id,"MOCK-"+shipment.id,shipment.provider+" SIMULATED");
        JsonNode response;
        if(shipment.provider==Provider.SHIPROCKET) {
            Map<String,Object> payload=shiprocketPayload(shipment,order,request);
            response=shipment.direction==Direction.RETURN?shiprocket.returns(srAuth(),payload):shiprocket.create(srAuth(),payload);
            return new Created(text(response,"order_id"),text(response,"shipment_id"),null,"SHIPROCKET");
        }
        Map<String,Object> payload=delhiveryPayload(shipment,order,request);
        response=delhivery.create(dlAuth(),"format=json&data="+URLEncoder.encode(write(payload),StandardCharsets.UTF_8));
        JsonNode pack=response.path("packages").path(0);
        if(!response.path("success").asBoolean() || !"Success".equalsIgnoreCase(pack.path("status").asText())) throw new IllegalStateException("Carrier did not confirm booking");
        String awb=text(pack,"waybill");
        return new Created(reference(shipment),awb,awb,"DELHIVERY");
    }
    public Assigned assign(Shipment row) {
        gate(row.provider,row.mode);
        if(row.mode==Mode.MOCK) return new Assigned("MOCK-"+row.id,row.provider+" SIMULATED");
        if(row.provider!=Provider.SHIPROCKET) throw new IllegalStateException("Missing Delhivery waybill");
        Map<String,Object> body=new LinkedHashMap<>(); body.put("shipment_id",row.shipmentId);
        if(row.direction==Direction.RETURN) body.put("is_return",1);
        JsonNode response=shiprocket.assign(srAuth(),body);
        if(response.path("awb_assign_status").asInt()!=1) throw new IllegalStateException("AWB was not assigned");
        JsonNode data=response.path("response").path("data");
        return new Assigned(text(data,"awb_code"),text(data,"courier_name"));
    }
    public String track(Shipment row) {
        gate(row.provider,row.mode);
        if(row.mode==Mode.MOCK) return "SIMULATED_BOOKED";
        if(row.provider==Provider.SHIPROCKET) {
            JsonNode tracking=shiprocket.track(srAuth(),row.awb).path("tracking_data");
            if(tracking.path("track_status").asInt()!=1) throw new IllegalStateException("Tracking unavailable");
            JsonNode item=tracking.path("shipment_track").path(0);
            if(!row.awb.equals(item.path("awb_code").asText())) throw new IllegalStateException("Tracking identity mismatch");
            return text(item,"current_status");
        }
        JsonNode item=delhivery.track(dlAuth(),row.awb).path("ShipmentData").path(0).path("Shipment");
        if(!row.awb.equals(item.path("AWB").asText())) throw new IllegalStateException("Tracking identity mismatch");
        return text(item.path("Status"),"Status");
    }
    public Label label(Shipment row) {
        gate(row.provider,row.mode);
        if(row.mode==Mode.MOCK) return new Label("SIMULATED",null,"TEST ONLY — no carrier-valid label is issued in MOCK mode");
        if(row.provider==Provider.DELHIVERY && row.direction==Direction.RETURN)
            return new Label("NOT_REQUIRED",null,"Delhivery reverse pickups do not require a packing slip; use the booked waybill");
        JsonNode response=row.provider==Provider.SHIPROCKET?shiprocket.label(srAuth(),Map.of("shipment_id",List.of(row.shipmentId))):delhivery.label(dlAuth(),row.awb,"True");
        String url;
        if(row.provider==Provider.SHIPROCKET) {
            if(response.path("label_created").asInt()!=1) throw new IllegalStateException("Label not created");
            url=text(response,"label_url");
        } else {
            // Delhivery accounts may expose PDF links on each package or at the root.
            JsonNode link=response.findValue("pdf_download_link");
            if(link==null || !link.isTextual()) throw new IllegalStateException("No provider PDF link returned");
            url=link.asText();
        }
        URI uri=URI.create(url);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalStateException("Invalid label link");
        return new Label("AVAILABLE",url,"Provider label link; may expire. Not proof of pickup or delivery.");
    }
    public String pickup(Shipment row,Pickup request) {
        gate(row.provider,row.mode);
        if(row.mode==Mode.MOCK) return "SIMULATED";
        if(row.provider==Provider.DELHIVERY && row.direction==Direction.RETURN) return "AUTOMATIC_REVERSE_PICKUP";
        if(row.provider==Provider.SHIPROCKET) {
            JsonNode result=shiprocket.pickup(srAuth(),Map.of("shipment_id",List.of(row.shipmentId)));
            if(result.path("pickup_status").asInt()!=1) throw new IllegalStateException("Pickup not confirmed");
        } else {
            JsonNode result=delhivery.pickup(dlAuth(),Map.of("pickup_time",request.time(),"pickup_date",request.date().toString(),
                    "pickup_location",required("delhivery.pickup-location"),"expected_package_count",1));
            text(result,"pickup_id");
        }
        return "SCHEDULED";
    }
    Map<String,Object> shiprocketPayload(Shipment shipment,PurchaseOrder order,Booking r) {
        Map<String,Object> p=new LinkedHashMap<>(); JsonNode a=read(order.addressJson);
        p.put("order_id",reference(shipment)); p.put("order_date",LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
        boolean reverse=shipment.direction==Direction.RETURN;
        String prefix=reverse?"pickup_":"billing_";
        p.put(prefix+"customer_name",r.recipientName()); p.put(prefix+"last_name","");
        p.put(prefix+"address",a.path("line1").asText()); p.put(prefix+"address_2",a.path("line2").asText(""));
        p.put(prefix+"city",a.path("city").asText()); p.put(prefix+"state",a.path("state").asText());
        p.put(prefix+"country","India"); p.put(prefix+"pincode",a.path("postalCode").asText());
        p.put(prefix+"phone",r.recipientPhone().substring(3)); p.put(prefix+"isd_code","91"); p.put(prefix+"email",r.recipientEmail());
        if(reverse) {
            p.put("channel_id",required("shiprocket.channel-id"));
            p.put("shipping_customer_name",required("warehouse.name")); p.put("shipping_last_name","");
            for(String field:List.of("address","city","state","pincode","email","phone")) p.put("shipping_"+field,required("warehouse."+field));
            p.put("shipping_country","India"); p.put("shipping_isd_code","91");
        } else { p.put("pickup_location",required("shiprocket.pickup-location")); p.put("shipping_is_billing",true); }
        List<Map<String,Object>> items=new ArrayList<>();
        for(JsonNode item:read(order.itemsJson)) {
            Map<String,Object> line=new LinkedHashMap<>(); line.put("name",item.path("name").asText()); line.put("sku",item.path("productId").asText());
            line.put("units",item.path("quantity").asInt()); line.put("selling_price",item.path("price").decimalValue());
            line.put("hsn",r.hsn()); items.add(line);
        }
        p.put("order_items",items); p.put("sub_total",order.subtotal==null?order.total:order.subtotal);
        p.put("total_discount",order.discount==null?BigDecimal.ZERO:order.discount);
        p.put("payment_method",reverse?"PREPAID":"CASH_ON_DELIVERY".equals(order.paymentMethod)?"COD":"Prepaid");
        p.put("weight",r.weightKg()); p.put("length",r.lengthCm()); p.put("breadth",r.breadthCm()); p.put("height",r.heightCm());
        if(r.ewaybill()!=null) p.put("ewaybill_no",r.ewaybill());
        return p;
    }
    Map<String,Object> delhiveryPayload(Shipment shipment,PurchaseOrder order,Booking r) {
        JsonNode a=read(order.addressJson); Map<String,Object> p=new LinkedHashMap<>();
        p.put("name",r.recipientName()); p.put("phone",r.recipientPhone()); p.put("add",a.path("line1").asText()+" "+a.path("line2").asText(""));
        p.put("pin",a.path("postalCode").asText()); p.put("city",a.path("city").asText()); p.put("state",a.path("state").asText()); p.put("country","India");
        p.put("order",reference(shipment)); p.put("client",required("delhivery.client-name"));
        boolean cod=shipment.direction==Direction.FORWARD && "CASH_ON_DELIVERY".equals(order.paymentMethod);
        p.put("payment_mode",shipment.direction==Direction.RETURN?"Pickup":cod?"COD":"Prepaid");
        p.put("total_amount",order.total); p.put("cod_amount",cod?order.total:BigDecimal.ZERO);
        p.put("weight",r.weightKg().multiply(new BigDecimal("1000"))); p.put("shipment_length",r.lengthCm()); p.put("shipment_width",r.breadthCm()); p.put("shipment_height",r.heightCm());
        p.put("products_desc","Ecommerce order "+order.id); int quantity=0;
        for(JsonNode item:read(order.itemsJson)) quantity+=item.path("quantity").asInt();
        p.put("quantity",quantity); p.put("seller_name",required("warehouse.name")); p.put("seller_gst_tin",required("warehouse.gstin")); p.put("hsn_code",r.hsn());
        p.put("return_name",required("warehouse.name")); p.put("return_phone",required("warehouse.phone"));
        p.put("return_add",required("warehouse.address")); p.put("return_pin",required("warehouse.pincode"));
        p.put("return_city",required("warehouse.city")); p.put("return_state",required("warehouse.state")); p.put("return_country","India");
        if(r.ewaybill()!=null) p.put("ewbn",r.ewaybill());
        return Map.of("shipments",List.of(p),"pickup_location",Map.of("name",required("delhivery.pickup-location")));
    }
    private String reference(Shipment s) { return s.orderId+(s.direction==Direction.RETURN?"-R":"-F"); }
    private String srAuth() { return "Bearer "+required("shiprocket.token"); }
    private String dlAuth() { return "Token "+required("delhivery.token"); }
    private JsonNode read(String value) { try { return json.readTree(value); } catch(Exception ex) { throw new IllegalStateException("Invalid order snapshot"); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception ex) { throw new IllegalStateException("Invalid shipment payload"); } }
    private String text(JsonNode node,String key) {
        String value=node.path(key).asText("");
        if(value.isBlank() || value.length()>200) throw new IllegalStateException("Provider response missing "+key);
        return value;
    }
}
