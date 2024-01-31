package fr.jhoudmon.reportGenerator;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import fr.jhoudmon.reportGenerator.exception.InvalidCallException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JsonQLDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

public class GeneratorServlet extends GenericServlet {

	private static final long serialVersionUID = 4043794618410108072L;

	private static final String METHOD_POST = "POST";
	private static final String GENERATOR_PATH = "/generate";

	@Override
	public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

		HttpServletRequest request;
		HttpServletResponse response;

		if (!(req instanceof HttpServletRequest && res instanceof HttpServletResponse)) {
			throw new ServletException("non-HTTP request or response");
		}

		request = (HttpServletRequest) req;
		response = (HttpServletResponse) res;

		service(request, response);

	}

	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method = req.getMethod();
		String pathInfo = req.getPathInfo();

		if (method.equals(METHOD_POST) && pathInfo.equals(GENERATOR_PATH)) {
			
			try {
				
				String templateName;
				Object jsonData;
				
				StringBuilder textBuilder = new StringBuilder();
			    try (Reader reader = new BufferedReader(new InputStreamReader
			      (req.getInputStream(), StandardCharsets.UTF_8))) {
			        int c = 0;
			        while ((c = reader.read()) != -1) {
			            textBuilder.append((char) c);
			        }
			    }
			
				JSONObject jsonObject = new JSONObject(textBuilder.toString());
				try {
					templateName = (String) jsonObject.get("template");
				} catch (JSONException jsE) {
					throw new InvalidCallException("template property not found");
				}
				
				try {
					jsonData = jsonObject.get("data");
				} catch (JSONException jsE) {
					throw new InvalidCallException("data property not found");
				}
				InputStream is = new ByteArrayInputStream(jsonData.toString().getBytes());
				
				String templateBaseDir = System.getProperty("TEMPLATES_BASE_PATH") + "/" + templateName;
				JasperDesign jasperDesign = JRXmlLoader.load(templateBaseDir + "/" + templateName + ".jrxml");
				JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);

				Map<String, Object> parameters = new HashMap<>();
				parameters.put("TEMPLATE_BASE_DIR", templateBaseDir);
				JsonQLDataSource jsonDataSource = new JsonQLDataSource(is);
				jsonDataSource.setDatePattern("yyyy-MM-dd");
				jsonDataSource.setNumberPattern("###0.###;###0.###-");
				jsonDataSource.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
				JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, jsonDataSource);

				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);

				resp.setStatus(200);
				resp.setContentType("application/pdf");
				resp.setHeader("Content-Disposition", "inline; filename=" + templateName + ".pdf");
				JasperExportManager.exportReportToPdfStream(jasperPrint, resp.getOutputStream());
			} catch (InvalidCallException e) {
				PrintStream ps = new PrintStream(resp.getOutputStream());
				resp.setStatus(400);
				resp.setContentType("text/plain");
				ps.println(e.getMessage());
			} catch (Throwable t) {
				PrintStream ps = new PrintStream(resp.getOutputStream());
				resp.setStatus(500);
				resp.setContentType("text/plain");
				ps.println(t.getMessage());
				t.printStackTrace(ps);
			}

		} else {
			resp.setStatus(404);
			resp.setContentType("text/plain");
		}
	}

}
