package okapi.acq;

import java.io.IOException;
import java.io.InputStream;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import static io.vertx.core.http.HttpHeaders.*;

public class MainVerticle extends AbstractVerticle {
  private static final String JSON = "application/json";
  private static final String TEXT = "text/plain";
  private static final String HTML = "text/html";
  private static final String X_OKAPI_TENANT = "X-Okapi-Tenant";
  private static final int CIRC_API_PORT = 8081;
  private static final int ACQ_API_PORT = 8082;
  private static final String SERVER = "localhost";
  private static final String TENANT = "hbz";
  String authorization = "a2VybWl0Omtlcm1pdA";

  private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Future<Void> fut) {
    Router router = Router.router(vertx);
    final int port = Integer.parseInt(System.getProperty("port", "8079"));
    router.route("/acq/*").handler(BodyHandler.create());
    
    router.get   ("/acq/funds"       ).produces(HTML).handler(r -> this.html     (r, "templates/funds.html"));
    router.get   ("/acq/funds"       ).produces(JSON).handler(r -> this.listAcq  (r, "/apis/funds"));
    router.post  ("/acq/funds"       )               .handler(this::createFund);
    
    router.get   ("/acq/funds/:id"   ).produces(HTML).handler(r -> this.html     (r, "templates/fund.html"));
    router.get   ("/acq/funds/:id"   ).produces(JSON).handler(r -> this.listAcq  (r, "/apis/funds"));
    router.put   ("/acq/funds/:id"   )               .handler(r -> this.putAcq   (r, "/apis/funds"));
    router.delete("/acq/funds/:id"   ).produces(TEXT).handler(r -> this.deleteAcq(r, "/apis/funds"));

    router.get   ("/acq/invoices"    ).produces(HTML).handler(r -> this.html     (r, "templates/invoices.html"));
    router.get   ("/acq/invoices"    ).produces(JSON).handler(r -> this.listAcq  (r, "/apis/invoices"));
    router.post  ("/acq/invoices"    )               .handler(this::createInvoice);
    
    router.get   ("/acq/invoices/:id").produces(HTML).handler(r -> this.html     (r, "templates/invoice.html"));
    router.get   ("/acq/invoices/:id").produces(JSON).handler(r -> this.listAcq  (r, "/apis/invoices"));
    router.put   ("/acq/invoices/:id")               .handler(r -> this.putAcq   (r, "/apis/invoices"));
    router.delete("/acq/invoices/:id").produces(TEXT).handler(r -> this.deleteAcq(r, "/apis/invoices"));

    vertx.createHttpServer().requestHandler(router::accept).listen(port, result -> {
      if (result.succeeded()) {
        fut.complete();
      } else {
        fut.fail(result.cause());
      }
    });
  }

  private String finalPath(RoutingContext routingContext, String path) {
      String id = routingContext.request().getParam("id");
      return (id == null) ? path : path + "/" + id;
  }
  
  private Buffer resource(String filePath) {
    try {
      InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
      if (stream == null) {
        logger.error("Resource not found: ", filePath);
        return null;
      }
      Buffer buffer = Buffer.buffer(stream.available());
      int length;
      byte[] data = new byte[16384];
  
      while (true) {
        length = stream.read(data);
        if (length < 0) {  // end of file
          break;
        }
        buffer.appendBytes(data, 0, length);
      }
  
      return buffer;
    }
    catch (IOException e) {
      logger.error(filePath, e);
    }
    return null;
  }
  
  private void html(RoutingContext routingContext, String filePath) {
    routingContext.response().setStatusCode(200).putHeader(CONTENT_TYPE, HTML).end(resource(filePath));
  }
  
  private void list(RoutingContext routingContext, int port, String path) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.get(port, SERVER, finalPath(routingContext, path),
        response -> response.bodyHandler(buffer -> {
          try {
            routingContext.response().putHeader(CONTENT_TYPE, JSON).end(buffer);
          } catch (Exception e) {
            logger.error(e);
          }
        }))
        .putHeader(ACCEPT, JSON)
        .putHeader(AUTHORIZATION, authorization)
        .putHeader(X_OKAPI_TENANT, TENANT)
        .end();
  }
  
  private void listAcq(RoutingContext routingContext, String path) {
    list(routingContext, ACQ_API_PORT, path);
  }
  
  private void listCirc(RoutingContext routingContext, String path) {
    list(routingContext, CIRC_API_PORT, path);
  }

  private void delete(RoutingContext routingContext, int port, String path) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.delete(port, SERVER, finalPath(routingContext, path),
        response -> response.bodyHandler(buffer -> {
          try {
            routingContext.response().putHeader(CONTENT_TYPE, TEXT)
              .setStatusCode(response.statusCode()).end(buffer);
          } catch (Exception e) {
            logger.error(e);
          }
        }))
        .putHeader(ACCEPT, TEXT)
        .putHeader(AUTHORIZATION, authorization)
        .putHeader(X_OKAPI_TENANT, TENANT)
        .end();
  }
  
  private void deleteAcq(RoutingContext routingContext, String path) {
    delete(routingContext, ACQ_API_PORT, path);
  }
  
  private void put(RoutingContext routingContext, int port, String path) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.put(port, SERVER, finalPath(routingContext, path),
        response -> response.bodyHandler(buffer -> {
          try {
            routingContext.response().putHeader(CONTENT_TYPE, TEXT)
              .setStatusCode(response.statusCode()).end(buffer);
          } catch (Exception e) {
            logger.error(e);
          }
        }))
        .putHeader(CONTENT_TYPE, JSON)
        .putHeader(ACCEPT, TEXT)
        .putHeader(AUTHORIZATION, authorization)
        .putHeader(X_OKAPI_TENANT, TENANT)
        .end(routingContext.getBody());
  }
  
  private void putAcq(RoutingContext routingContext, String path) {
    put(routingContext, ACQ_API_PORT, path);
  }
  
  private void post(RoutingContext routingContext, int port, String path, String content) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.post(port, SERVER, path,
        response -> response.bodyHandler(buffer -> {
          try {
            routingContext.response().putHeader(CONTENT_TYPE, JSON)
              .setStatusCode(response.statusCode()).end(buffer);
          } catch (Exception e) {
            logger.error(e);
          }
        }))
        .putHeader(CONTENT_TYPE, JSON)
        .putHeader(ACCEPT,       JSON)
        .putHeader(AUTHORIZATION, authorization)
        .putHeader(X_OKAPI_TENANT, TENANT)
        .end(content);
  }
  
  private void createFund(RoutingContext routingContext) {
    String id = Long.toString(Math.round(Math.random() * 999999999));
    String content = 
        "{"
      + "  '_id' : '" + id + "',"
      + "  'code' : 'Bioebooks',"
      + "  'name' : 'ebooks for Biology',"
      + "  'status' : {"
      + "    'value' : 'ACTIVE',"
      + "    'desc' : 'Active'"
      + "  },"
      + "  'description' : 'for use by the general public',"
      + "  'currency' : {"
      + "    'value' : 'USD',"
      + "    'desc' : 'US Dollar'"
      + "  },"
      + "  'fiscal_period' : {"
      + "    'value' : '7',"
      + "    'desc' : '07/07/2017 - 07/07/2017'"
      + "  }"
      + "}";
    content = content.replace('\'', '"');
    
    post(routingContext, ACQ_API_PORT, "/apis/funds", content);
  }
  
  private void createInvoice(RoutingContext routingContext) {
    String id = Long.toString(Math.round(Math.random() * 999999999));
    String content =
        "{"
      + "  '_id': '" + id + "',"
      + "  'vendor_invoice_number': '1234567890',"
      + "  'invoice_date': '1975-03-30',"
      + "  'total_amount': 100,"
      + "  'currency': {"
      + "    'value': 'USD',"
      + "    'desc': 'US Dollar'"
      + "  },"
      + "  'vendor_code': {"
      + "    'value': 'AutoEDI_MainCode',"
      + "    'desc': 'AutoEDI_Name'"
      + "  },"
      + "  'vendor_contact_person': {"
      + "    'first_name': 'joe',"
      + "    'last_name': 'z'"
      + "  },"
      + "  'payment': {"
      + "    'prepaid': false,"
      + "    'tracking_purpose_only': false,"
      + "    'send_to_erp': true,"
      + "    'payment_status': 'NOT_PAID',"
      + "    'date_of_payment': '1975-03-30',"
      + "    'final_amount_paid': 0,"
      + "    'payment_currency': {"
      + "      'value': 'USD',"
      + "      'desc': 'US Dollar'"
      + "    }"
      + "  },"
      + "  'payment_method': {"
      + "    'value': 'ACCOUNTINGDEPARTMENT',"
      + "    'desc': 'Accounting Department'"
      + "  },"
      + "  'created_from': {"
      + "    'value': 'EDI',"
      + "    'desc': 'EDIteur Invoice Message'"
      + "  },"
      + "  'invoice_status': {"
      + "    'value': 'ACTIVE',"
      + "    'desc': 'Active'"
      + "  },"
      + "  'approved_by': 'You',"
      + "  'additional_charges': {"
      + "    'shipment': 0,"
      + "    'overhead': 0,"
      + "    'insurance': 0,"
      + "    'discount': 0,"
      + "    'use_pro_rata': false"
      + "  },"
      + "  'invoice_vat': {"
      + "    'taxable': false,"
      + "    'vat_percent': 0,"
      + "    'type': {"
      + "      'value': 'INCLUSIVE',"
      + "      'desc': 'Inclusive'"
      + "    },"
      + "    'vat_amount': 0,"
      + "    'vat_expended_from_fund': true"
      + "  },"
      + "  'note': 'abc',"
      + "  'invoice_line': []"
      + "}";
    content = content.replace('\'', '"');
    
    post(routingContext, ACQ_API_PORT, "/apis/invoices", content);
  }
}