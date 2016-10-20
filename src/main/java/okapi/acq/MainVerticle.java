package okapi.acq;

import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static io.vertx.core.http.HttpHeaders.AUTHORIZATION;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.io.FileNotFoundException;
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

public class MainVerticle extends AbstractVerticle {
  private static final String JSON = "application/json";
  private static final String TEXT = "text/plain";
  private static final String HTML = "text/html";
  private static final String X_OKAPI_TENANT = "X-Okapi-Tenant";
  private static final String authorization = "a2VybWl0Omtlcm1pdA";
  private static final String DMOD_FUNDS = "/funds";
  private static final String DMOD_INVOICES = "/invoices";
  private static final String DMOD_PO_LINES = "/po_lines";

  private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  private int dataPort = 8083;
  private String dataServer = "localhost";
  private String tenant = "hbz";

  private void configure() {
    dataServer = config().getString("data.api.server", dataServer);
    dataPort = config().getInteger("data.api.port", dataPort);
    tenant = config().getString("data.api.tenant", tenant);
  }

  @Override
  public void start(Future<Void> fut) {
    configure();

    Router router = Router.router(vertx);
    final int port = Integer.parseInt(System.getProperty("port", "8079"));
    router.route("/acq/*").handler(BodyHandler.create());

    router.get   ("/acq/funds"       ).produces(HTML).handler(r -> this.html     (r, "templates/funds.html"));
    router.get   ("/acq/funds"       ).produces(JSON).handler(r -> this.listAcq  (r, DMOD_FUNDS));
    router.post  ("/acq/funds"       )               .handler(this::createFund);

    router.get   ("/acq/funds/:id"   ).produces(HTML).handler(r -> this.html     (r, "templates/fund.html"));
    router.get(   "/acq/funds/:id"   ).produces(JSON).handler(r -> this.listAcq  (r, DMOD_FUNDS));
    router.put(   "/acq/funds/:id"   )               .handler(r -> this.putAcq   (r, DMOD_FUNDS));
    router.delete("/acq/funds/:id"   ).produces(TEXT).handler(r -> this.deleteAcq(r, DMOD_FUNDS));

    router.get   ("/acq/invoices"    ).produces(HTML).handler(r -> this.html     (r, "templates/invoices.html"));
    router.get   ("/acq/invoices"    ).produces(JSON).handler(r -> this.listAcq  (r, DMOD_INVOICES));
    router.post  ("/acq/invoices"    )               .handler(this::createInvoice);

    router.get   ("/acq/invoices/:id").produces(HTML).handler(r -> this.html     (r, "templates/invoice.html"));
    router.get   ("/acq/invoices/:id").produces(JSON).handler(r -> this.listAcq  (r, DMOD_INVOICES));
    router.put   ("/acq/invoices/:id")               .handler(r -> this.putAcq   (r, DMOD_INVOICES));
    router.delete("/acq/invoices/:id").produces(TEXT).handler(r -> this.deleteAcq(r, DMOD_INVOICES));

    router.get   ("/acq/po_lines"    ).produces(HTML).handler(r -> this.html     (r, "templates/po_lines.html"));
    router.get   ("/acq/po_lines"    ).produces(JSON).handler(r -> this.listAcq  (r, DMOD_PO_LINES));
    router.post  ("/acq/po_lines"    )               .handler(this::createPoLine);

    router.get   ("/acq/po_lines/:id").produces(HTML).handler(r -> this.html     (r, "templates/po_line.html"));
    router.get   ("/acq/po_lines/:id").produces(JSON).handler(r -> this.listAcq  (r, DMOD_PO_LINES));
    router.put   ("/acq/po_lines/:id")               .handler(r -> this.putAcq   (r, DMOD_PO_LINES));
    router.delete("/acq/po_lines/:id").produces(TEXT).handler(r -> this.deleteAcq(r, DMOD_PO_LINES));

    router.getWithRegex("/acq/[a-zA-Z0-9._-]+(\\.css|\\.js)").handler(this::sendFile);

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
    try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath)) {
      if (stream == null) {
        throw new FileNotFoundException(filePath);
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
    httpClient.get(port, dataServer, finalPath(routingContext, path),
        response -> response.bodyHandler(buffer -> {
          try {
            routingContext.response().putHeader(CONTENT_TYPE, JSON).end(buffer);
          } catch (Exception e) {
            logger.error(e);
          }
        }))
        .putHeader(ACCEPT, JSON)
        .putHeader(AUTHORIZATION, authorization)
        .putHeader(X_OKAPI_TENANT, tenant)
        .end();
  }

  private void listAcq(RoutingContext routingContext, String path) {
    list(routingContext, dataPort, path);
  }

  private void delete(RoutingContext routingContext, int port, String path) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.delete(port, dataServer, finalPath(routingContext, path),
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
        .putHeader(X_OKAPI_TENANT, tenant)
        .end();
  }

  private void deleteAcq(RoutingContext routingContext, String path) {
    delete(routingContext, dataPort, path);
  }

  private void put(RoutingContext routingContext, int port, String path) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.put(port, dataServer, finalPath(routingContext, path),
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
        .putHeader(X_OKAPI_TENANT, tenant)
        .end(routingContext.getBody());
  }

  private void putAcq(RoutingContext routingContext, String path) {
    put(routingContext, dataPort, path);
  }

  private void post(RoutingContext routingContext, int port, String path, String content) {
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.post(port, dataServer, path,
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
        .putHeader(X_OKAPI_TENANT, tenant)
        .end(content);
  }

  private void createFund(RoutingContext routingContext) {
    String content =
        "{"
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

    post(routingContext, dataPort, DMOD_FUNDS, content);
  }

  private void createInvoice(RoutingContext routingContext) {
    String content =
        "{"
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

    post(routingContext, dataPort, DMOD_INVOICES, content);
  }

  private void createPoLine(RoutingContext routingContext) {
    String content =
        "{"
      + "  'po_line_status': {"
      + "    'value': 'SENT',"
      + "    'desc': 'sent to vendor'"
      + "  },"
      + "  'owner': {"
      + "    'value': 'MITLIBMATH',"
      + "    'desc': 'Math Library'"
      + "  },"
      + "  'type': {"
      + "    'value': 'PRINT_ONETIME',"
      + "    'desc': ''"
      + "  },"
      + "  'vendor': {"
      + "    'value': 'YBP',"
      + "    'desc': ''"
      + "  },"
      + "  'vendor_account_CODE': 'YBP_CODE',"
      + "  'acquisition_method_CODE': {"
      + "    'value': 'VENDOR_SYSTEM',"
      + "    'desc': 'Purchased at Vendor System'"
      + "  },"
      + "  'rush': false,"
      + "  'price': {"
      + "    'sum': '150.0',"
      + "    'po_currency': {"
      + "      'value': 'USD',"
      + "      'desc': 'US Dollar'"
      + "    }"
      + "  },"
      + "  'fund_distributions': ["
      + "    {"
      + "      'fund_code': 'Bioebooks',"
      + "      'amount': {"
      + "        'sum': 123.5,"
      + "        'currency': 'USD'"
      + "      }"
      + "    }"
      + "  ],"
      + "  'vendor_reference_number': 'ybp-1234567890',"
      + "  'ebook_url': '',"
      + "  'source_type': 'API',"
      + "  'po_number': '0987654321',"
      + "  'invoice_reference': '',"
      + "  'resource_metadata': '/abc/v1/bibs/99113721800121',"
      + "  'access_provider': '',"
      + "  'material_type': 'BOOK',"
      + "  'block_alert_on_po_line': ["
      + "    {"
      + "      'value': 'FUNDMISS',"
      + "      'desc': 'Fund is missing'"
      + "    }"
      + "  ],"
      + "  'note': [],"
      + "  'location': [],"
      + "  'created_date': '',"
      + "  'update_date': '',"
      + "  'renewal_period': '',"
      + "  'renewal_date': ''"
      + "}";
    content = content.replace('\'', '"');

    post(routingContext, dataPort, DMOD_PO_LINES, content);
  }

  /**
   * Extract the name (the last component) of a path.
   * path2name("") = ""
   * path2name("/") = ""
   * path2name("a.htm") = "a.htm"
   * path2name("/a/b/") = ""
   * path2name("/a/b/c.htm") = "c.htm"
   * @param path - where to search for the name
   * @return last component of the path, may be an empty string
   */
  private String path2name(String path) {
    int slashPos = path.lastIndexOf('/');
    if (slashPos < 0) {
      return path;
    }
    return path.substring(slashPos + 1);
  }

  private void sendFile(RoutingContext context) {
    String [] whitelist = {
      "META-INF/resources/webjars/bootstrap/3.3.7/css/bootstrap.min.css",
      "META-INF/resources/webjars/jquery/1.11.1/jquery.min.js"
    };
    String slashName = "/" + path2name(context.request().path());
    for (String file : whitelist) {
      if (file.endsWith(slashName)) {
        context.response().sendFile(file);
        return;
      }
    }
    context.response().setStatusCode(404).end("File not found: " + context.request().path());
  }
}