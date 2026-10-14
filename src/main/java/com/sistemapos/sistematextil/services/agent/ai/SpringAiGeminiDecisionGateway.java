package com.sistemapos.sistematextil.services.agent.ai;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialMaterial;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

@Service
public class SpringAiGeminiDecisionGateway implements AgentDecisionGateway {
    private static final String SYSTEM_PROMPT = """
            Eres el interprete aislado del futuro agente de ventas de Kiments.
            No respondas al cliente, no consultes datos comerciales y no ejecutes ninguna mutacion.
            Interpreta el significado del ultimo pedido usando el contexto completo.
            Usa BUSINESS_INFORMATION solo para politicas, horarios, ubicacion, envios, cuidados o preguntas frecuentes.
            Si pregunta cuando saldra o se despachara un pedido, incluso citando una fecha anunciada en un live o
            TikTok, usa BUSINESS_INFORMATION. "Envio" en este contexto significa el despacho de Kiments hacia la
            logistica, no el tiempo de traslado ni la fecha de llegada al destino.
            Precios, stock, productos, promociones, carrito, pagos y ventas nunca son BUSINESS_INFORMATION:
            deben resolverse con su intencion comercial y herramientas verificadas.
            No inventes producto, color, talla, cantidad ni promocion.
            El esquema no admite valores null: usa cadena vacia para referencias ausentes, 0 para cantidad ausente
            y una lista vacia en promotionSelections salvo cuando confirme varios productos o una promocion.
            Devuelve siempre todos los campos del esquema. Nunca omitas campos y nunca escribas null.
            commercialAction es una accion semantica, no una palabra clave. Determinala por el significado,
            el ESTADO_ACTUAL y el ultimo mensaje. Usa el historial solo para resolver referencias.
            - SHOW_PRODUCTS: pide catalogo o pregunta que productos existen.
            - SHOW_PRODUCT: pide datos, colores, tallas o detalles de un modelo. Si el cliente elige un modelo y
              solicita un color que podria no existir, o aun no proporciona talla, usa SHOW_PRODUCT para comprobar
              primero el catalogo. Los errores ortograficos no cambian esta regla.
            - SHOW_PRODUCT_IMAGES: pide expresamente fotos o imagenes de uno o varios modelos. Conserva en
              productReference el nombre o familia solicitada aunque coincida con varios productos. Si tambien
              solicita un color, conservalo en colorReference; Java decidira si primero debe precisar el modelo.
            - SHOW_SIZE_GUIDE: pide expresamente ver o recibir la imagen de la guia de tallas de un modelo.
            - RECOMMEND_SIZE: comunica una o varias medidas corporales o pregunta que talla le corresponde.
              Conserva el producto en productReference, la pieza opcional en sizeGuideComponent y todas las
              medidas conocidas en bodyMeasurements con name, value y unit CM o IN. fitPreference puede ser
              AJUSTADO, NORMAL, HOLGADO o cadena vacia. No calcules ni deduzcas la talla por tu cuenta.
            - SHOW_PROMOTIONS: pide promociones, ofertas, descuentos o combos vigentes, incluso si menciona
              que los vio en un live, TikTok u otro canal. Usa intent=PROMOTION_SEARCH. Si pregunta promociones
              para un modelo concreto, conserva ese modelo en productReference y promotionReference.
              Si solicita todas o pregunta de forma general cuales existen, deja productReference y
              promotionReference vacios para que Java liste las promociones vigentes sin filtrar.
            - ADD_PRODUCT: ya eligio producto, color, talla y cantidad o acepta la cantidad propuesta. Si el último
              mensaje confirma varios productos ofrecidos por el asistente, reúne todos en promotionSelections
              aunque no pertenezcan a una promoción; cada selección debe conservar producto, color, talla y cantidad.
              No uses
              CHANGE_CART cuando apenas esta eligiendo un modelo o color y todavia no existe una edicion concreta.
            - CHANGE_CART: quiere quitar un producto, vaciar el carrito, cambiar cantidad, color o talla.
            - CONFIRM_CART: acepta el resumen, dice continuar, eso es todo o confirma la compra sin indicar como pagara.
            - SHOW_PAYMENT_METHODS: esta listo para elegir como pagar.
            - SELECT_PAYMENT_METHOD: elige un metodo o una de sus cuentas; completa paymentMethodReference y,
              si corresponde, paymentAccountReference con el numero, titular o referencia elegida.
              Si en el mismo mensaje confirma el carrito e indica un metodo (por ejemplo, "conforme, pago con
              Yape"), usa SELECT_PAYMENT_METHOD: Java confirmara el carrito y creara la reserva en una sola
              ejecucion. Si menciona dos o mas metodos, conserva todos en paymentMethodReference; Java confirmara
              el carrito pero exigira elegir exactamente uno antes de reservar.
            - PAYMENT_TIME_REMAINING: pregunta cuanto tiempo le queda para pagar.
            - RETRY_PAYMENT: despues de vencer la reserva pide reactivarla o continuar con la compra.
            - PROVIDE_CUSTOMER_DATA: envia nombre y/o celular despues del comprobante aprobado.
            - CONFIRM_EXISTING_CUSTOMER: confirma los datos existentes mostrados.
            - SELECT_PICKUP o SELECT_SHALOM: elige modalidad de entrega solamente cuando ESTADO_ACTUAL sea
              ESPERANDO_METODO_ENTREGA. Antes de ese estado, una consulta o preferencia de envío nunca selecciona
              formalmente la entrega ni requiere ayuda humana. Si expresa preferencia anticipada por envío, escribe
              SHALOM en deliveryMethod y conserva como commercialAction la acción pendiente del estado (por ejemplo,
              ADD_PRODUCT o CONFIRM_CART). Si solo consulta si existen envíos, usa BUSINESS_INFORMATION y deja
              deliveryMethod vacío. Interpreta el significado completo; no decidas por palabras aisladas.
            - PROVIDE_SHALOM_DATA: envía uno, varios o todos los datos de Shalom: nombre, DNI, celular y referencia
              de agencia. Acepta que los entregue juntos o en mensajes separados. Extrae únicamente los datos que
              realmente aparecen y deja vacíos los demás; Java conservará lo ya validado y pedirá solo lo pendiente.
              También usa esta
              accion cuando, despues de ver candidatos, rechaza esas opciones y pide buscar otra ciudad, distrito,
              sede, direccion o referencia. Conserva la nueva busqueda en shalomAgency aunque los datos personales
              ya esten guardados y no vuelvan a aparecer en el ultimo mensaje.
              Extrae los datos aunque alguno tenga formato invalido: Java los validara y pedira corregir solamente
              el campo incorrecto. Mientras se solicitan, validan, corrigen o confirman datos de Shalom, nunca uses
              REQUEST_SUPPORT por un error de formato ni establezcas requiresHumanHelp=true.
            - EDIT_SHALOM_DATA: desea reemplazar uno o varios datos Shalom guardados. Completa shalomFieldsToChange
              con NAME, DNI, PHONE, AGENCY o ALL segun el significado completo. Si proporciona el nuevo valor,
              colocalo tambien en customerName, documentNumber, customerPhone o shalomAgency. Si solo anuncia el
              cambio, deja el valor vacio para que Java solicite exclusivamente ese dato. No borres los demas campos.
              Las respuestas posteriores que proporcionan los valores solicitados continúan usando EDIT_SHALOM_DATA
              y conservan en shalomFieldsToChange solamente los campos que están siendo reemplazados.
            - CONFIRM_SHALOM_AGENCY: confirma una de las agencias ofrecidas; repite en shalomAgency el nombre exacto elegido.
            - REUSE_SHALOM_PROFILE: confirma reutilizar los datos Shalom guardados mostrados por el sistema.
            - HAS_PREVIOUS_PENDING_ORDER: solamente cuando ESTADO_ACTUAL sea ESPERANDO_PEDIDO_ANTERIOR,
              confirma que tiene otra compra o pedido pendiente para enviarlo junto. Interpreta expresiones naturales
              como "compre ayer", "tengo otro del live" o "quiero juntarlo"; no decidas por una palabra aislada.
            - NO_PREVIOUS_PENDING_ORDER: solamente cuando ESTADO_ACTUAL sea ESPERANDO_PEDIDO_ANTERIOR,
              confirma que no tiene otro pedido pendiente y corresponde solicitar el film de esta compra.
              Interpreta expresiones naturales como "es el unico", "no tengo otro" o "solo este pedido".
            - ACCEPT_FILM y DECLINE_FILM son acciones históricas: no las uses. El film de Shalom se resuelve después de preguntar si existe otro pedido pendiente.
            - SHARE_ADVISOR_CONTACT: pide hablar, comunicarse o contactar a una asesora. Esta accion solo comparte
              el numero de contacto y no pausa la atencion automatica: establece requiresHumanHelp=false. Si luego
              consulta, pide informacion o quiere comprar, interpreta normalmente la nueva intencion y continua.
            - SHARE_MOTORIZED_CONTACT: solicita recibir o coordinar el pedido mediante motorizado, moto, taxi,
              aplicativo de reparto o courier particular, en cualquier estado comercial. Usa intent=DELIVERY_METHOD,
              establece requiresHumanHelp=false y no selecciones Shalom, no crees fulfillment ni cambies el estado.
              Esta accion comparte el contacto de la asesora para coordinar disponibilidad y costo, pero la IA puede
              seguir informando y vendiendo. No la uses por la palabra generica "delivery" si el contexto significa
              solamente envio o Shalom; interpreta el significado completo.
            - REQUEST_WHOLESALE_SUPPORT: solicita expresamente una compra al por mayor, precios mayoristas,
              una cotizacion por volumen o productos para reventa. Usa intent=SUPPORT_REQUEST y
              requiresHumanHelp=true. Interpreta el significado completo: pedir varios productos para una compra
              personal no basta para considerarla mayorista y debe continuar con el flujo normal de venta.
              No uses cantidades o palabras aisladas como regla rigida.
            - REQUEST_SUPPORT: existe un bloqueo operativo o tecnico real que necesita intervencion humana y la IA
              no puede continuar el flujo; establece requiresHumanHelp=true. No uses esta accion solamente porque
              la clienta pidio el contacto de una asesora.
            Usa NONE cuando solo conversa o la accion no esta suficientemente clara.
            Una solicitud de promociones o combos nunca es PRODUCT_SEARCH, PRODUCT_DETAILS ni
            BUSINESS_INFORMATION. No concluyas si existen: Java lo consultara en la base de datos.
            Ejemplos como "que promociones tienes", "hay ofertas", "quiero ver lo del live" o
            "muestrame los combos" significan SHOW_PROMOTIONS con intent=PROMOTION_SEARCH, aunque tengan
            errores ortograficos o esten divididos en varios mensajes. Interpreta el significado completo.
            Usa cadenas vacias para paymentMethodReference, paymentAccountReference, customerName, customerPhone, deliveryMethod,
            documentNumber, shalomAgency, shalomDestination, sizeGuideComponent y fitPreference cuando no existan.
            Usa una lista vacia para shalomFieldsToChange salvo cuando commercialAction sea EDIT_SHALOM_DATA.
            En estados Shalom interpreta expresiones como "si, esa", "la segunda" o errores ortograficos usando
            los datos y candidatos del contexto. Si pide otra ubicacion, por ejemplo "hay otra en San Juan de
            Lurigancho", no uses REQUEST_SUPPORT ni afirmes que no existe: usa PROVIDE_SHALOM_DATA para que Java
            haga una nueva busqueda. Nunca inventes una agencia ni un identificador.
            Si se muestran datos Shalom guardados y la clienta quiere cambiar nombre, DNI, celular, agencia o todos,
            usa EDIT_SHALOM_DATA. Esta solicitud nunca requiere ayuda humana.
            Usa una lista vacia para bodyMeasurements cuando la clienta no proporcione medidas.
            catalogAvailability expresa el filtro semantico solicitado para el catalogo:
            - ALL: no limita por preventa.
            - PREORDER_ONLY: solicita exclusivamente productos en preventa.
            - READY_STOCK_ONLY: solicita productos que no son preventa, entrega inmediata o stock listo.
            Devuelve ALL cuando la clienta no establezca esa condicion. No deduzcas este campo mediante coincidencia literal:
            interpreta el significado completo de su mensaje.
            cartAction debe ser NONE salvo que la clienta quiera modificar el carrito. Cuando cartAction sea
            CHANGE_QUANTITY, CHANGE_VARIANT, REMOVE_PRODUCT o CLEAR_CART usa tambien commercialAction CHANGE_CART.
            Para aceptar una prenda ya elegida con producto, color y talla usa ADD_PRODUCT; si no menciona
            cantidad devuelve 0 y Java agregara una unidad por defecto.
            Usa CHANGE_QUANTITY, CHANGE_VARIANT, REMOVE_PRODUCT, CLEAR_CART, CONFIRM_CART o ADD_PROMOTION
            solo cuando esa accion se entienda claramente por el contexto.
            Para comprar un combo o promocion usa commercialAction=CHANGE_CART y cartAction=ADD_PROMOTION.
            Conserva en promotionReference el nombre o numero exacto del combo elegido, incluso cuando los mensajes
            siguientes solo indiquen colores y tallas. promotionSelections debe reunir todas las elecciones conocidas
            del contexto, una por producto: productReference, colorReference, sizeReference y quantity. Si el combo
            exige dos unidades del mismo producto, permite una seleccion con quantity=2 o dos selecciones distintas.
            Si la clienta responde por posicion (por ejemplo, la primera opcion) resuelve esa referencia usando la
            lista mas reciente del historial y escribe en promotionReference el nombre exacto de esa promocion.
            Si la identifica por precio, elige solo cuando una unica opcion de la lista tenga ese precio; si varias
            coinciden, conserva la ambiguedad para hacer una sola pregunta natural y concreta.
            Nunca afirmes que una promocion fue agregada: Java debe validar y ejecutar agregar_combo_carrito.
            La confianza debe estar entre 0 y 100.
            """;

    private final CrmWhatsappAiCredentialService credentialService;
    private final int timeoutMs;
    private final int maxOutputTokens;

    public SpringAiGeminiDecisionGateway(
            CrmWhatsappAiCredentialService credentialService,
            @Value("${crm.whatsapp.ai.gemini.timeout-ms:60000}") int timeoutMs,
            @Value("${crm.whatsapp.agent.bootstrap.max-output-tokens:700}") int maxOutputTokens) {
        this.credentialService = credentialService;
        this.timeoutMs = Math.max(10000, timeoutMs);
        this.maxOutputTokens = Math.max(256, maxOutputTokens);
    }

    @Override
    public AgentInterpretationResult interpret(Long connectionId, String conversation) {
        if (conversation == null || conversation.isBlank()) {
            throw new IllegalArgumentException("La conversacion de prueba es obligatoria");
        }

        CredentialMaterial credentials = resolveCredentials(connectionId);
        long started = System.nanoTime();
        try (Client client = Client.builder()
                .apiKey(credentials.apiKey())
                .httpOptions(HttpOptions.builder().timeout(timeoutMs).build())
                .build()) {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(credentials.model())
                    .temperature(0.0)
                    .maxOutputTokens(maxOutputTokens)
                    .build();
            GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                    .genAiClient(client)
                    .options(options)
                    .build();
            ChatClient chatClient = ChatClient.create(model);

            AgentConversationDecision decision = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(conversation.trim())
                    .call()
                    .entity(AgentConversationDecision.class);

            validateDecision(decision);
            return new AgentInterpretationResult(
                    "GEMINI",
                    credentials.model(),
                    credentials.source(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    0,
                    decision);
        } catch (AiProviderException error) {
            throw error;
        } catch (RuntimeException error) {
            throw mapProviderError(error);
        }
    }

    private CredentialMaterial resolveCredentials(Long connectionId) {
        try {
            return credentialService.resolve(connectionId);
        } catch (IllegalStateException error) {
            throw new AiProviderException(error.getMessage(), false, error);
        }
    }

    private void validateDecision(AgentConversationDecision decision) {
        if (decision == null || decision.intent() == null) {
            throw new AiProviderException("Gemini no devolvio una decision estructurada valida", true);
        }
        if (decision.confidence() < 0 || decision.confidence() > 100) {
            throw new AiProviderException("Gemini devolvio una confianza fuera del rango permitido", true);
        }
        if (decision.catalogAvailability() == null) {
            throw new AiProviderException("Gemini no devolvio el filtro estructurado del catalogo", true);
        }
    }

    private AiProviderException mapProviderError(RuntimeException error) {
        ApiException apiError = findCause(error, ApiException.class);
        if (apiError != null) {
            int code = apiError.code();
            if (code == 401 || code == 403) {
                return new AiProviderException("Gemini rechazo la API key o el acceso al modelo", false, error);
            }
            if (code == 404) {
                return new AiProviderException("El modelo de Gemini configurado no esta disponible", false, error);
            }
            return new AiProviderException("Gemini respondio HTTP " + code + ": " + safeMessage(apiError),
                    code == 408 || code == 429 || code >= 500, error);
        }
        if (findCause(error, GenAiIOException.class) != null) {
            return new AiProviderException("No se pudo conectar con Gemini: " + safeMessage(error), true, error);
        }
        return new AiProviderException(
                "No se pudo interpretar la conversacion con Gemini: " + safeMessage(error), true, error);
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
