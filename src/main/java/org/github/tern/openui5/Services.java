package org.github.tern.openui5;

import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

@Path("services")
public class Services {
	@GET
	@Path("version")
	@Produces(MediaType.APPLICATION_JSON)
	public Response version() throws Exception {
		JsonObject json = Ui5.getMetadata();

		return Response.ok(json.toString()).build();
	}
	
	@GET
	@Path("/download")
	@Produces("application/javascript")
	public Response getFileEtat(@Context final HttpServletRequest request) throws Exception
	{
		final ResponseBuilder response = Response.ok(Ui5.generateOpenUi5());
		response.header("Content-Disposition", "attachment; filename=openui5.js");
		response.header("Set-Cookie", "fileDownload=true; path=/");
		return response.build();

	}
}