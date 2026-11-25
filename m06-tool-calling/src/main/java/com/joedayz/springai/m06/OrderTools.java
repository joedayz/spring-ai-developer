package com.joedayz.springai.m06;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Tools sobre base de datos y API externa.
 *
 * Seguridad: el cliente autenticado llega por {@link ToolContext}, que el modelo no ve ni puede alterar.
 * Cada tool valida que el pedido pertenezca a ese cliente. Nunca se recibe el cliente como parámetro del modelo.
 */
@Component
class OrderTools {

    static final String CUSTOMER_ID = "customerId";

    private final JdbcClient jdbc;
    private final CarrierClient carrier;

    OrderTools(JdbcClient jdbc, CarrierClient carrier) {
        this.jdbc = jdbc;
        this.carrier = carrier;
    }

    @Tool(description = "Lista los pedidos del cliente actual con su estado")
    List<Map<String, Object>> listMyOrders(ToolContext context) {
        return jdbc.sql("select id, item, total, status from orders where customer_id = :c order by id")
                .param("c", customer(context))
                .query().listOfRows();
    }

    @Tool(description = "Obtiene el detalle de un pedido del cliente actual")
    Map<String, Object> getOrder(@ToolParam(description = "Número de pedido") long orderId, ToolContext context) {
        return findOwned(orderId, context);
    }

    @Tool(description = "Consulta en el transportista el estado de envío de un pedido que ya fue despachado")
    CarrierClient.Tracking trackShipment(@ToolParam(description = "Número de pedido") long orderId,
                                         ToolContext context) {
        Object tracking = findOwned(orderId, context).get("TRACKING");
        if (tracking == null) {
            throw new IllegalStateException("El pedido " + orderId + " aún no tiene número de seguimiento");
        }
        return carrier.track(tracking.toString());
    }

    /** Acción con efectos: el bucle se pausa hasta que una persona la apruebe. */
    @Tool(description = "Cancela un pedido del cliente actual. Solo es posible si el pedido está en PROCESSING",
            requiresConfirmation = true)
    String cancelOrder(@ToolParam(description = "Número de pedido") long orderId, ToolContext context) {
        Map<String, Object> order = findOwned(orderId, context);
        if (!"PROCESSING".equals(order.get("STATUS"))) {
            throw new IllegalStateException(
                    "No se puede cancelar: el pedido " + orderId + " está en estado " + order.get("STATUS"));
        }
        jdbc.sql("update orders set status = 'CANCELLED' where id = :id").param("id", orderId).update();
        return "Pedido " + orderId + " cancelado";
    }

    private Map<String, Object> findOwned(long orderId, ToolContext context) {
        try {
            return jdbc.sql("select id, item, total, status, tracking from orders where id = :id and customer_id = :c")
                    .param("id", orderId)
                    .param("c", customer(context))
                    .query().singleRow();
        } catch (EmptyResultDataAccessException e) {
            // Mismo mensaje si no existe o si es de otro cliente: no se revela la existencia del pedido.
            throw new IllegalArgumentException("No se encontró el pedido " + orderId);
        }
    }

    private static String customer(ToolContext context) {
        return (String) context.getContext().get(CUSTOMER_ID);
    }
}
