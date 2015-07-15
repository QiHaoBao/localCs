package controllers;

import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringEscapeUtils;

import models.ClimateService;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;
import utils.RESTfulCalls;
import utils.RESTfulCalls.ResponseType;
import views.html.*;
import models.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ClimateServiceController extends Controller {

	final static Form<ClimateService> climateServiceForm = Form
			.form(ClimateService.class);
	
	public static Result home() {
		return ok(home.render());
	} 
	
	public static Result addAClimateService() {
		return ok(registerAClimateService.render(climateServiceForm));
	}

	public static Result showAllClimateServices() {
		List<ClimateService> climateServicesList = new ArrayList<ClimateService>();
		JsonNode climateServicesNode = RESTfulCalls.getAPI(Constants.URL_HOST
				+ Constants.CMU_BACKEND_PORT
				+ Constants.GET_ALL_CLIMATE_SERVICES);
		System.out.println("GET API: " + Constants.URL_HOST
				+ Constants.CMU_BACKEND_PORT
				+ Constants.GET_ALL_CLIMATE_SERVICES);
		// if no value is returned or error or is not json array
		if (climateServicesNode == null || climateServicesNode.has("error")
				|| !climateServicesNode.isArray()) {
			System.out.println("All climate services format has error!");
		}

		// parse the json string into object
		for (int i = 0; i < climateServicesNode.size(); i++) {
			JsonNode json = climateServicesNode.path(i);
			ClimateService oneService = new ClimateService();
			oneService.setName(json.path("name").asText());
			oneService.setPurpose(json.path("purpose").asText());
			// URL here is the dynamic page url
			String name = json.path("name").asText();
			String pageUrl = Constants.URL_SERVER + Constants.LOCAL_HOST_PORT + "/assets/html/service" + 
					name.substring(0, 1).toUpperCase() + name.substring(1) + ".html";
			oneService.setUrl(pageUrl);
			// newService.setCreateTime(json.path("createTime").asText());
			oneService.setScenario(json.path("scenario").asText());
			oneService.setVersionNo(json.path("versionNo").asText());
			oneService.setRootServiceId(json.path("rootServiceId").asLong());
			climateServicesList.add(oneService);
		}

		return ok(allClimateServices.render(climateServicesList,
				climateServiceForm));
	}

	public static Result addClimateService() {
		Form<ClimateService> cs = climateServiceForm.bindFromRequest();

		ObjectNode jsonData = Json.newObject();
		try {

			String originalClimateServiceName = cs.field("name").value();
			String newClimateServiceName = originalClimateServiceName.replace(
					' ', '-');

			// name should not contain spaces
			if (newClimateServiceName != null
					&& !newClimateServiceName.isEmpty()) {
				jsonData.put("name", newClimateServiceName);
			}
			jsonData.put("creatorId", 1); // TODO, since we don't have
											// login/account id yet use a
											// default val
			jsonData.put("purpose", cs.field("purpose").value());
			jsonData.put("url", cs.field("url").value());
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
			// get current date time with Date()
			Date date = new Date();
			jsonData.put("createTime", dateFormat.format(date));
			jsonData.put("scenario", cs.field("scenario").value());
			jsonData.put("versionNo", cs.field("version").value());
			jsonData.put("rootServiceId", cs.field("rootServiceId").value());

			// POST Climate Service JSON data
			JsonNode response = RESTfulCalls.postAPI(Constants.URL_HOST + Constants.CMU_BACKEND_PORT 
					+ Constants.ADD_CLIMATE_SERVICE, jsonData);

			// flash the response message
			Application.flashMsg(response);
		} catch (IllegalStateException e) {
			e.printStackTrace();
			Application.flashMsg(RESTfulCalls
					.createResponse(ResponseType.CONVERSIONERROR));
		} catch (Exception e) {
			e.printStackTrace();
			Application.flashMsg(RESTfulCalls
					.createResponse(ResponseType.UNKNOWN));
		}
		return redirect(routes.ClimateServiceController.addAClimateService());
	}

	public static Result serviceModels() {
		JsonNode jsonData = request().body().asJson();
		System.out.println("JSON data: " + jsonData);
		String url = jsonData.get("climateServiceCallUrl").toString();
		System.out.println("JPL climate service model call url: " + url);

		ObjectNode object = (ObjectNode) jsonData;
		object.remove("climateServiceCallUrl");

		System.out.println("JSON data after removing: " + (JsonNode) object);

		// from JsonNode to java String, always has "" quotes on the two sides
		JsonNode response = RESTfulCalls.postAPI(
				url.substring(1, url.length() - 1), (JsonNode) object);
		System.out.println("Response: " + response);

		// flash the response message
		Application.flashMsg(response);
		System.out
				.println(ok("Climate Service model has been called successfully!"));
		// return jsonData
		return ok(response);
	}

	// send dynamic page string
	public static Result passPageStr() {
		String str = request().body().asJson().get("pageString").toString();
		String name = request().body().asJson().get("name").toString();
		String purpose = request().body().asJson().get("purpose").toString();
		String url = request().body().asJson().get("url").toString();

		System.out.println("page string: " + str);
		System.out.println("climate service name: " + name);

		ObjectNode jsonData = Json.newObject();
		jsonData.put("pageString", str);

		// POST Climate Service JSON data to CMU 9020 backend
		// One copy in backend and one copy in frontend
		JsonNode response = RESTfulCalls.postAPI(Constants.URL_HOST
				+ Constants.CMU_BACKEND_PORT
				+ Constants.SAVE_CLIMATE_SERVICE_PAGE, jsonData);
		
		
		System.out.println("WARNING!!!!!!");
		// save page in front-end
		savePage(str, name, purpose, url);

		// flash the response message
		Application.flashMsg(response);
		return ok("Climate Service Page has been saved succussfully!");
	}

	public static void savePage(String str, String name, String purpose,
			String url) {

		// Remove delete button from preview page
		String result = str
				.replaceAll(
						"<td><button type=\\\\\"button\\\\\" class=\\\\\"btn btn-danger\\\\\" onclick=\\\\\"Javascript:deleteRow\\(this\\)\\\\\">delete</button></td>",
						"");

		result = StringEscapeUtils.unescapeJava(result);

		// remove the first char " and the last char " of result, name and
		// purpose
		result = result.substring(1, result.length() - 1);
		name = name.substring(1, name.length() - 1);
		purpose = purpose.substring(1, purpose.length() - 1);

		String str11 = Constants.htmlHead1;
		// System.out.println("head1: " + str11);
		String str12 = Constants.htmlHead2;
		// System.out.println("head2: " + str12);
		String str13 = Constants.htmlHead3;
		// System.out.println("head3: " + str13);

		String str21 = Constants.htmlTail1;
		String str22 = Constants.htmlTail2;

		result = str11 + name + str12 + purpose + str13 + result + str21
				+ url.substring(1, url.length() - 1) + str22;

		name = name.replace(" ", "");

		// Java file name cannot start with number and chars like '_' '-'...
		String location = "public/html/" + "service"
				+ name.substring(0, 1).toUpperCase() + name.substring(1)
				+ ".html";

		File theDir = new File("public/html");
		
		// if the directory does not exist, create it
		if (!theDir.exists()) {
			System.out.println("creating directory: public/html");
			boolean create = false;

			try {
				theDir.mkdir();
				create = true;
			} catch (SecurityException se) {
				// handle it
			}
			if (create) {
				System.out.println("DIR created");
			}
		}

		try {
			File file = new File(location);
			BufferedWriter output = new BufferedWriter(new FileWriter(file));
			output.write(result);
			output.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void flashMsg(JsonNode jsonNode) {
		Iterator<Entry<String, JsonNode>> it = jsonNode.fields();
		while (it.hasNext()) {
			Entry<String, JsonNode> field = it.next();
			flash(field.getKey(), field.getValue().asText());
		}
	}
	
	public static List<ClimateService> getMostRecentlyAdded() {

		List<ClimateService> climateServices = new ArrayList<ClimateService>();

		JsonNode climateServicesNode = RESTfulCalls
				.getAPI(Constants.URL_HOST
						+ Constants.CMU_BACKEND_PORT
						+ Constants.GET_MOST_RECENTLY_ADDED_CLIMATE_SERVICES_CALL);
		//System.out.print(GET_MOST_RECENTLY_ADDED_CLIMATE_SERVICES_CALL);
		// if no value is returned or error or is not json array
		if (climateServicesNode == null || climateServicesNode.has("error")
				|| !climateServicesNode.isArray()) {
			return climateServices;
		}

		// parse the json string into object
		for (int i = 0; i < climateServicesNode.size(); i++) {
			JsonNode json = climateServicesNode.path(i);
			ClimateService newService = new ClimateService();
			//long newServiceId = json.get("id").asLong();
			//newService.setId(newServiceId);
			newService.setName(json.get(
					"name").asText());
			newService.setPurpose(json.findPath("purpose").asText());
			newService.setUrl(json.findPath("url").asText());
			//newService.setCreateTime(json.findPath("createTime").asText());
			newService.setScenario(json.findPath("scenario").asText());
			newService.setVersionNo(json.findPath("versionNo").asText());
			newService.setRootServiceId(json.findPath("rootServiceId").asLong());
			climateServices.add(newService);
		}
		return climateServices;
	}
	
	
	public static List<ClimateService> getMostPopular() {

		List<ClimateService> climateServices = new ArrayList<ClimateService>();

		JsonNode climateServicesNode = RESTfulCalls
				.getAPI(Constants.URL_HOST + Constants.CMU_BACKEND_PORT + Constants.GET_MOST_POPULAR_CLIMATE_SERVICES_CALL);

		// if no value is returned or error or is not json array
		if (climateServicesNode == null || climateServicesNode.has("error")
				|| !climateServicesNode.isArray()) {
			return climateServices;
		}

		// parse the json string into object
		for (int i = 0; i < climateServicesNode.size(); i++) {
			JsonNode json = climateServicesNode.path(i);
			ClimateService newService = new ClimateService();
			//newService.setId(json.get("id").asText());
			newService.setName(json.get(
					"name").asText());
			newService.setPurpose(json.findPath("purpose").asText());
			newService.setUrl(json.findPath("url").asText());
			//newService.setCreateTime(json.findPath("createTime").asText());
			newService.setScenario(json.findPath("scenario").asText());
			newService.setVersionNo(json.findPath("versionNo").asText());
			newService.setRootServiceId(json.findPath("rootServiceId").asLong());
			climateServices.add(newService);
		}
		return climateServices;
	}
	
	public static Result mostRecentlyAddedClimateServices() {
		return ok(mostRecentlyAddedServices.render(getMostRecentlyAdded(),
				climateServiceForm));
	}
	
	public static Result mostPopularServices() {
		return ok(mostPopularServices.render(getMostPopular(),
				climateServiceForm));
	}
	
	public static List<ClimateService> getMostRecentlyUsed() {

		List<ClimateService> climateServices = new ArrayList<ClimateService>();

		JsonNode climateServicesNode = RESTfulCalls.getAPI(Constants.URL_HOST
				+ Constants.CMU_BACKEND_PORT
				+ Constants.GET_MOST_RECENTLY_USED_CLIMATE_SERVICES_CALL);
		
		if (climateServicesNode == null || climateServicesNode.has("error")
				|| !climateServicesNode.isArray()) {
			return climateServices;
		}

		// parse the json string into object
		for (int i = 0; i < climateServicesNode.size(); i++) {
			JsonNode json = climateServicesNode.path(i);
			ClimateService newService = new ClimateService();
			newService.setId(json.get("id").asLong());
			newService.setName(json.get("name").asText());
			newService.setPurpose(json.findPath("purpose").asText());
			newService.setUrl(json.findPath("url").asText());
			//newService.setCreateTime(json.findPath("createTime").asText());
			newService.setScenario(json.findPath("scenario").asText());
			newService.setVersionNo(json.findPath("versionNo").asText());
			newService.setRootServiceId(json.findPath("rootServiceId").asLong());
			climateServices.add(newService);
		}
		return climateServices;
	}
	
	public static Result mostRecentlyUsedClimateServices() {
		return ok(mostRecentlyUsedServices.render(getMostRecentlyUsed(),
				climateServiceForm));
	}
	
	
	
	public static Result getConfigurationByConfId() {
		String output = "";

		TwoDVarMap twoDVarMap = new TwoDVarMap();
		TwoDVarZonalMean twoDVarZonalMean = new TwoDVarZonalMean();
		ScatterHistogramTwoVar scatterHistogram = new ScatterHistogramTwoVar();
		ThreeDVarAvgVertical4Profile threeDVarAvgVertical = new ThreeDVarAvgVertical4Profile();
		DiffPlotTwoTimeAveragedVar diffPlotTwoTimeAvg = new DiffPlotTwoTimeAveragedVar();
		ThreeDVar2DSlice threeDVar2DSlice = new ThreeDVar2DSlice();
		TwoDVarTimeSeries twoDVarTimeSeries = new TwoDVarTimeSeries();
		ThreeDVarZonalMean threeDVarZonalMean = new ThreeDVarZonalMean();
		ConditionalSampling conditionalSampling = new ConditionalSampling();

		try {
			DynamicForm df = DynamicForm.form().bindFromRequest();
			String logId = df.field("logId").value();

			if (logId == null || logId.isEmpty()) {
				Application.flashMsg(RESTfulCalls.createResponse(ResponseType.UNKNOWN));
				return notFound("confId is null or empty");
			}

			// Call API
			//JsonNode response = RESTfulCalls.callAPI("http://localhost:9008/getConfigurationByConId/json");
			JsonNode response = RESTfulCalls.getAPI(Constants.NEW_BACKEND + Constants.SERVICE_EXECUTION_LOG + Constants.SERVICE_EXECUTION_LOG_GET + logId);

			int configurationId = response.path("serviceConfiguration").path("id").asInt();

			JsonNode responseConfigItems = RESTfulCalls.getAPI(Constants.NEW_BACKEND + Constants.CONFIG_ITEM + Constants.GET_CONFIG_ITEMS_BY_CONFIG + configurationId);
//			Console.print(responseSpec.toString());
			String serviceName = response.path("climateService").path("name").asText();

			//TODO:
			if (serviceName.equals("2-D-Variable-Zonal-Mean")) {
				//TODO: DO NOT USE node.findPath(key)!!!!!  use find(key) instead to get your immediate children if you know the json structure (and we do).
				//TODO: (con't) findPath returns the first occurence of a key string, including GRANDCHILDREN
				for (int i = 0; i < responseConfigItems.size(); i++) {
					String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
					String parameterValue = responseConfigItems.get(i).path("value").textValue();

					//	String parameterName = response.get(i).path("parameterPurpose").textValue();
					if (parameterName.equals("data source")) {
						twoDVarZonalMean.setDataSource(parameterValue);

					} else if (parameterName.equals("variable name")) {
						twoDVarZonalMean.setVariableName(parameterValue);

					} else if (parameterName.equals("start year-month")) {
						twoDVarZonalMean.setStartYearMonth(parameterValue);

					} else if (parameterName.equals("end year-month")) {
						twoDVarZonalMean.setEndYearMonth(parameterValue);
					} else if (parameterName.equals("select months")) {
						String[] months = parameterValue.split(",");

						for (int j = 0; j < months.length; j++) {
							if (months[j].equals("1")) {
								twoDVarZonalMean.addMonth("jan");
							} else if (months[j].equals("2")) {
								twoDVarZonalMean.addMonth("feb");
							} else if (months[j].equals("3")) {
								twoDVarZonalMean.addMonth("mar");
							} else if (months[j].equals("4")) {
								twoDVarZonalMean.addMonth("apr");
							} else if (months[j].equals("5")) {
								twoDVarZonalMean.addMonth("may");
							} else if (months[j].equals("6")) {
								twoDVarZonalMean.addMonth("jun");
							} else if (months[j].equals("7")) {
								twoDVarZonalMean.addMonth("jul");
							} else if (months[j].equals("8")) {
								twoDVarZonalMean.addMonth("aug");
							} else if (months[j].equals("9")) {
								twoDVarZonalMean.addMonth("sep");
							} else if (months[j].equals("10")) {
								twoDVarZonalMean.addMonth("oct");
							} else if (months[j].equals("11")) {
								twoDVarZonalMean.addMonth("nov");
							} else if (months[j].equals("12")) {
								twoDVarZonalMean.addMonth("dec");
							}

						}
						twoDVarZonalMean.changeSelectMonths();
					} else if (parameterName.equals("start lat (deg)")) {
						twoDVarZonalMean.setStartLat(parameterValue);
					} else if (parameterName.equals("end lat (deg)")) {
						twoDVarZonalMean.setEndLat(parameterValue);
					} else if (parameterName.equals("variable scale")) {
						twoDVarZonalMean.setVariableScale(parameterValue);
					}
				}
				twoDVarZonalMean.setExecutionPurpose(response.path("purpose").textValue());
				twoDVarZonalMean.setImage(response.path("plotUrl").textValue());
				twoDVarZonalMean.setDataURL(response.path("dataUrl").textValue());
				return ok(views.TwoDVariableZonelMean.render(twoDVarZonalMean));
			}
			else if (serviceName.equals("2-D-Variable-Map")) {//Old ID 12
				for (int i = 0; i < responseConfigItems.size(); i++) {
					String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
					String parameterValue = responseConfigItems.get(i).path("value").textValue();
					if (parameterName.equals("model")) {
						twoDVarMap.setDataSource(parameterValue);

					} else if (parameterName.equals("var")) {
						twoDVarMap.setVariableName(parameterValue);

					} else if (parameterName.equals("startT")) {
						twoDVarMap.setStartYearMonth(parameterValue);

					} else if (parameterName.equals("endT")) {
						twoDVarMap.setEndYearMonth(parameterValue);
					} else if (parameterName.equals("months")) {
						String[] months = parameterValue.split(",");

						for (int j = 0; j < months.length; j++) {
							if (months[j].equals("1")) {
								twoDVarMap.addMonth("jan");
							} else if (months[j].equals("2")) {
								twoDVarMap.addMonth("feb");
							} else if (months[j].equals("3")) {
								twoDVarMap.addMonth("mar");
							} else if (months[j].equals("4")) {
								twoDVarMap.addMonth("apr");
							} else if (months[j].equals("5")) {
								twoDVarMap.addMonth("may");
							} else if (months[j].equals("6")) {
								twoDVarMap.addMonth("jun");
							} else if (months[j].equals("7")) {
								twoDVarMap.addMonth("jul");
							} else if (months[j].equals("8")) {
								twoDVarMap.addMonth("aug");
							} else if (months[j].equals("9")) {
								twoDVarMap.addMonth("sep");
							} else if (months[j].equals("10")) {
								twoDVarMap.addMonth("oct");
							} else if (months[j].equals("11")) {
								twoDVarMap.addMonth("nov");
							} else if (months[j].equals("12")) {
								twoDVarMap.addMonth("dec");
							}
						}
						twoDVarMap.changeSelectMonths();
					} else if (parameterName.equals("lat1")) {
						twoDVarMap.setStartLat(parameterValue);
					} else if (parameterName.equals("lat2")) {
						twoDVarMap.setEndLat(parameterValue);
					} else if (parameterName.equals("lon1")) {
						twoDVarMap.setStartLon(parameterValue);
					} else if (parameterName.equals("lon2")) {
						twoDVarMap.setEndLon(parameterValue);
					} else if (parameterName.equals("scale")) {
						twoDVarMap.setVariableScale(parameterValue);
						}
				}
				twoDVarMap.setExecutionPurpose(response.path("purpose").textValue());
				twoDVarMap.setImage(response.path("plotUrl").textValue());
				twoDVarMap.setDataURL(response.path("dataUrl").textValue());
				return ok(views.twoDVariableMap.render(twoDVarMap));
			}
			//Old ID 21
//			else if (serviceName.equals("2-D-Variable-Map")){
//				for(int i=0; i<response.size(); i++){
//					String parameterName = responseConfigItems.get(i).get("parameter").get("purpose").textValue();
//					String parameterValue = responseConfigItems.get(i).get("value").textValue();
//					if(parameterName.equals("model2")){
//						para_21.setDataSourceE(parameterValue);
//						
//					}else if(parameterName.equals("model1")){
//						para_21.setDataSourceP(parameterValue);
//						
//					}else if(parameterName.equals("var2")){
//						para_21.setVariableNameE(parameterValue);
//						
//					}else if(parameterName.equals("var1")){
//						para_21.setVariableNameP(parameterValue);
//					}else if(parameterName.equals("pre1")){
//						para_21.setPressureRangeP(parameterValue);
//					}
//					else if(parameterName.equals("pre2")){
//						para_21.setPressureRangeE(parameterValue);
//					}else if(parameterName.equals("startT")){
//						para_21.setStartYearMonth(parameterValue);
//					}else if(parameterName.equals("endT")){
//						para_21.setEndYearMonth(parameterValue);
//					}else if(parameterName.equals("lon1")){
//						para_21.setStartLon(parameterValue);
//					}else if(parameterName.equals("lon2")){
//						para_21.setEndLon(parameterValue);
//					}else if(parameterName.equals("lat1")){
//						Console.print("aaa"+parameterValue);
//						para_21.setStartLat(parameterValue);
//					}else if(parameterName.equals("lat2")){
//						para_21.setEndLat(parameterValue);
//					}else if(parameterName.equals("months")){
//						String[] months = parameterValue.split(",");
//						
//						for(int j=0; j<months.length; j++){
//							if(months[j].equals("1")){
//								para_21.addMonth("jan");
//							}else if(months[j].equals("2")){
//								para_21.addMonth("feb");
//							}else if(months[j].equals("3")){
//								para_21.addMonth("mar");
//							}else if(months[j].equals("4")){
//								para_21.addMonth("apr");
//							}else if(months[j].equals("5")){
//								para_21.addMonth("may");
//							}else if(months[j].equals("6")){
//								para_21.addMonth("jun");
//							}else if(months[j].equals("7")){
//								para_21.addMonth("jul");
//							}else if(months[j].equals("8")){
//								para_21.addMonth("aug");
//							}else if(months[j].equals("9")){
//								para_21.addMonth("sep");
//							}else if(months[j].equals("10")){
//								para_21.addMonth("oct");
//							}else if(months[j].equals("11")){
//								para_21.addMonth("nov");
//							}else if(months[j].equals("12")){
//								para_21.addMonth("dec");
//							}
//						}
//					}
//				}
//				twoDVarMap.setExecutionPurpose(response.path("purpose").textValue());
//				twoDVarMap.setImage(response.path("plotUrl").textValue());
//				twoDVarMap.setDataURL(response.path("dataUrl").textValue());
//				return ok(views.html.climate.twoDVariableMap.render(twoDVarMap));
//			}
				else if (serviceName.equals("Conditional-Sampling"))
				{    //Old ID 21
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model2")) {
							conditionalSampling.setDataSourceE(parameterValue);

						} else if (parameterName.equals("model1")) {
							conditionalSampling.setDataSourceP(parameterValue);

						} else if (parameterName.equals("var2")) {
							conditionalSampling.setVariableNameE(parameterValue);

						} else if (parameterName.equals("var1")) {
							conditionalSampling.setVariableNameP(parameterValue);
						} else if (parameterName.equals("pre1")) {
							conditionalSampling.setPressureRangeP(parameterValue);
						} else if (parameterName.equals("pre2")) {
							conditionalSampling.setPressureRangeE(parameterValue);
						} else if (parameterName.equals("startT")) {
							conditionalSampling.setStartYearMonth(parameterValue);
						} else if (parameterName.equals("endT")) {
							conditionalSampling.setEndYearMonth(parameterValue);
						} else if (parameterName.equals("lon1")) {
							conditionalSampling.setStartLon(parameterValue);
						} else if (parameterName.equals("lon2")) {
							conditionalSampling.setEndLon(parameterValue);
						} else if (parameterName.equals("lat1")) {
							conditionalSampling.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							conditionalSampling.setEndLat(parameterValue);
						} else if (parameterName.equals("months")) {
							String[] months = parameterValue.split(",");

							for (int j = 0; j < months.length; j++) {
								if (months[j].equals("1")) {
									conditionalSampling.addMonth("jan");
								} else if (months[j].equals("2")) {
									conditionalSampling.addMonth("feb");
								} else if (months[j].equals("3")) {
									conditionalSampling.addMonth("mar");
								} else if (months[j].equals("4")) {
									conditionalSampling.addMonth("apr");
								} else if (months[j].equals("5")) {
									conditionalSampling.addMonth("may");
								} else if (months[j].equals("6")) {
									conditionalSampling.addMonth("jun");
								} else if (months[j].equals("7")) {
									conditionalSampling.addMonth("jul");
								} else if (months[j].equals("8")) {
									conditionalSampling.addMonth("aug");
								} else if (months[j].equals("9")) {
									conditionalSampling.addMonth("sep");
								} else if (months[j].equals("10")) {
									conditionalSampling.addMonth("oct");
								} else if (months[j].equals("11")) {
									conditionalSampling.addMonth("nov");
								} else if (months[j].equals("12")) {
									conditionalSampling.addMonth("dec");
								}

							}
							conditionalSampling.changeSelectMonths();
						} else if (parameterName.equals("bin_min")) {
							conditionalSampling.setBin_min(parameterValue);
						} else if (parameterName.equals("bin_max")) {
							conditionalSampling.setBin_max(parameterValue);
						} else if (parameterName.equals("bin_n")) {
							conditionalSampling.setBin_n(parameterValue);
						} else if (parameterName.equals("displayOpt")) {
							int paramBit=Integer.parseInt(parameterValue);
							int bitmaskX = 0x1;
							int bitmaskY = 0x2;
							int bitmaskZ = 0x4;

							conditionalSampling.setX(Integer.toString(paramBit & bitmaskX, 2));
							conditionalSampling.setY(Integer.toString((paramBit & bitmaskY)>>1, 2));
							conditionalSampling.setZ(Integer.toString((paramBit & bitmaskZ)>>2, 2));

						}
					}
					conditionalSampling.setExecutionPurpose(response.path("purpose").textValue());
					conditionalSampling.setImage(response.path("plotUrl").textValue());
					conditionalSampling.setDataURL(response.path("dataUrl").textValue());
					return ok(views.conditionalSampling.render(conditionalSampling));
				}else if (serviceName.equals("2-D-Variable-Time-Series")){    //Old ID 1595
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model")) {
							twoDVarTimeSeries.setDataSource(parameterValue);

						} else if (parameterName.equals("var")) {
							twoDVarTimeSeries.setVariableName(parameterValue);

						} else if (parameterName.equals("startT")) {
							twoDVarTimeSeries.setStartYearMonth(parameterValue);

						} else if (parameterName.equals("endT")) {
							twoDVarTimeSeries.setEndYearMonth(parameterValue);
						} else if (parameterName.equals("lat1")) {
							twoDVarTimeSeries.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							twoDVarTimeSeries.setEndLat(parameterValue);
						} else if (parameterName.equals("lon1")) {
							twoDVarTimeSeries.setStartLon(parameterValue);
						} else if (parameterName.equals("lon2")) {
							twoDVarTimeSeries.setEndLon(parameterValue);
						} else if (parameterName.equals("scale")) {
							twoDVarTimeSeries.setVariableScale(parameterValue);
						}
					}
					twoDVarTimeSeries.setExecutionPurpose(response.path("purpose").textValue());
					twoDVarTimeSeries.setImage(response.path("plotUrl").textValue());
					twoDVarTimeSeries.setDataURL(response.path("dataUrl").textValue());
					return ok(views.twoDVariableTimeSeries.render(twoDVarTimeSeries));
			}else if (serviceName.equals("3-D-Variable-Zonal-Mean")){
					// old ID 1597
					String press1 = "";
					String press2 = "";
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model")) {
							threeDVarZonalMean.setDataSource(parameterValue);

						} else if (parameterName.equals("var")) {
							threeDVarZonalMean.setVariableName(parameterValue);

						} else if (parameterName.equals("startT")) {
							threeDVarZonalMean.setStartYearMonth(parameterValue);

						} else if (parameterName.equals("endT")) {
							threeDVarZonalMean.setEndYearMonth(parameterValue);
						} else if (parameterName.equals("lat1")) {
							threeDVarZonalMean.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							threeDVarZonalMean.setEndLat(parameterValue);
						} else if (parameterName.equals("pres1")) {
							press1 = parameterValue;
							//Console.println(press1);
						} else if (parameterName.equals("pres2")) {
							press2 = parameterValue;

						} else if (parameterName.equals("months")) {
							String[] months = parameterValue.split(",");

							for (int j = 0; j < months.length; j++) {
								if (months[j].equals("1")) {
									threeDVarZonalMean.addMonth("jan");
								} else if (months[j].equals("2")) {
									threeDVarZonalMean.addMonth("feb");
								} else if (months[j].equals("3")) {
									threeDVarZonalMean.addMonth("mar");
								} else if (months[j].equals("4")) {
									threeDVarZonalMean.addMonth("apr");
								} else if (months[j].equals("5")) {
									threeDVarZonalMean.addMonth("may");
								} else if (months[j].equals("6")) {
									threeDVarZonalMean.addMonth("jun");
								} else if (months[j].equals("7")) {
									threeDVarZonalMean.addMonth("jul");
								} else if (months[j].equals("8")) {
									threeDVarZonalMean.addMonth("aug");
								} else if (months[j].equals("9")) {
									threeDVarZonalMean.addMonth("sep");
								} else if (months[j].equals("10")) {
									threeDVarZonalMean.addMonth("oct");
								} else if (months[j].equals("11")) {
									threeDVarZonalMean.addMonth("nov");
								} else if (months[j].equals("12")) {
									threeDVarZonalMean.addMonth("dec");
								}
							}
						} else if (parameterName.equals("scale")) {
							if (parameterValue.equals("2")) {
								threeDVarZonalMean.setPressureScale("2");
								threeDVarZonalMean.setColorScale("0");
							} else if (parameterValue.equals("0")) {
								threeDVarZonalMean.setPressureScale("0");
								threeDVarZonalMean.setColorScale("0");
							} else if (parameterValue.equals("4")) {
								threeDVarZonalMean.setPressureScale("0");
								threeDVarZonalMean.setColorScale("4");
							} else if (parameterValue.equals("6")) {
								threeDVarZonalMean.setPressureScale("2");
								threeDVarZonalMean.setColorScale("4");
							}
						}
					}
					if (!press1.isEmpty() & !press2.isEmpty())
						threeDVarZonalMean.setPressureRange("" + (Integer.parseInt(press1) / 100) + "," + (Integer.parseInt(press2) / 100));
					threeDVarZonalMean.setExecutionPurpose(response.path("purpose").textValue());
					threeDVarZonalMean.setImage(response.path("plotUrl").textValue());
					threeDVarZonalMean.setDataURL(response.path("dataUrl").textValue());
					return ok(views.threeDVariableZonalMean.render(threeDVarZonalMean));
			}else if (serviceName.equals("Scatter-and-Histogram-Plot-of-Two-Variables")){                    //"19")){
					scatterHistogram.setPressureLevel1("N/A");
					scatterHistogram.setPressureLevel2("N/A");
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model1")) {
							scatterHistogram.setSource1(parameterValue);

						} else if (parameterName.equals("model2")) {
							scatterHistogram.setSource2(parameterValue);

						} else if (parameterName.equals("var1")) {
							scatterHistogram.setVaribaleName1(parameterValue);

						} else if (parameterName.equals("var2")) {
							scatterHistogram.setVaribaleName2(parameterValue);
						} else if (parameterName.equals("startT")) {
							scatterHistogram.setStartYear(parameterValue);
						} else if (parameterName.equals("endT")) {
							scatterHistogram.setEndYear(parameterValue);
						} else if (parameterName.equals("lon1")) {
							scatterHistogram.setStartLon(parameterValue);
						} else if (parameterName.equals("lon2")) {
							scatterHistogram.setEndLon(parameterValue);
						} else if (parameterName.equals("lat1")) {
							scatterHistogram.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							scatterHistogram.setEndLat(parameterValue);
						} else if (parameterName.equals("nSample")) {
							scatterHistogram.setSamples(parameterValue);
						}
					}
					scatterHistogram.setExecutionPurpose(response.path("purpose").textValue());
					scatterHistogram.setImage(response.path("plotUrl").textValue());
					scatterHistogram.setDataUrl(response.path("dataUrl").textValue());
					return ok(views.scatterAndHistogramTwoVariable.render(scatterHistogram));
			}else if (serviceName.equals("Difference-Plot-of-Two-Time-Averaged-Variables")){            //"20")){
					diffPlotTwoTimeAvg.setPressureLevel1("N/A");
					Console.println(diffPlotTwoTimeAvg.getPressureLevel1());
					diffPlotTwoTimeAvg.setPressureLevel2("N/A");
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model1")) {
							diffPlotTwoTimeAvg.setSource1(parameterValue);

						} else if (parameterName.equals("model2")) {
							diffPlotTwoTimeAvg.setSource2(parameterValue);

						} else if (parameterName.equals("var1")) {
							diffPlotTwoTimeAvg.setVaribaleName1(parameterValue);

						} else if (parameterName.equals("var2")) {
							diffPlotTwoTimeAvg.setVaribaleName2(parameterValue);
						} else if (parameterName.equals("pre1")) {
							diffPlotTwoTimeAvg.setPressureLevel1(parameterValue);
						} else if (parameterName.equals("startT")) {
							diffPlotTwoTimeAvg.setStartYear(parameterValue);
						} else if (parameterName.equals("endT")) {
							diffPlotTwoTimeAvg.setEndYear(parameterValue);
						} else if (parameterName.equals("lon1")) {
							diffPlotTwoTimeAvg.setStartLon(parameterValue);
						} else if (parameterName.equals("lon2")) {
							diffPlotTwoTimeAvg.setEndLon(parameterValue);
						} else if (parameterName.equals("lat1")) {
							diffPlotTwoTimeAvg.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							diffPlotTwoTimeAvg.setEndLat(parameterValue);
						}
					}
					diffPlotTwoTimeAvg.setExecutionPurpose(response.path("purpose").textValue());
					diffPlotTwoTimeAvg.setImage(response.path("plotUrl").textValue());
					diffPlotTwoTimeAvg.setDataUrl(response.path("dataUrl").textValue());
					return ok(views.DifferencePlotTwoTimeAveragedVariables.render(diffPlotTwoTimeAvg));
			}else if (serviceName.equals("3-D-Variable-Average-Vertical-4Profile")){ //"18")){
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model")) {
							threeDVarAvgVertical.setDataSource(parameterValue);

						} else if (parameterName.equals("var")) {
							threeDVarAvgVertical.setVariableName(parameterValue);

						} else if (parameterName.equals("startT")) {
							threeDVarAvgVertical.setStartYearMonth(parameterValue);
						} else if (parameterName.equals("endT")) {
							threeDVarAvgVertical.setEndYearMonth(parameterValue);
						} else if (parameterName.equals("lon1")) {
							threeDVarAvgVertical.setStartLon(parameterValue);
						} else if (parameterName.equals("lon2")) {
							threeDVarAvgVertical.setEndLon(parameterValue);
						} else if (parameterName.equals("lat1")) {
							threeDVarAvgVertical.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							threeDVarAvgVertical.setEndLat(parameterValue);
						} else if (parameterName.equals("months")) {
							String[] months = parameterValue.split(",");

							for (int j = 0; j < months.length; j++) {
								if (months[j].equals("1")) {
									threeDVarAvgVertical.addMonth("jan");
								} else if (months[j].equals("2")) {
									threeDVarAvgVertical.addMonth("feb");
								} else if (months[j].equals("3")) {
									threeDVarAvgVertical.addMonth("mar");
								} else if (months[j].equals("4")) {
									threeDVarAvgVertical.addMonth("apr");
								} else if (months[j].equals("5")) {
									threeDVarAvgVertical.addMonth("may");
								} else if (months[j].equals("6")) {
									threeDVarAvgVertical.addMonth("jun");
								} else if (months[j].equals("7")) {
									threeDVarAvgVertical.addMonth("jul");
								} else if (months[j].equals("8")) {
									threeDVarAvgVertical.addMonth("aug");
								} else if (months[j].equals("9")) {
									threeDVarAvgVertical.addMonth("sep");
								} else if (months[j].equals("10")) {
									threeDVarAvgVertical.addMonth("oct");
								} else if (months[j].equals("11")) {
									threeDVarAvgVertical.addMonth("nov");
								} else if (months[j].equals("12")) {
									threeDVarAvgVertical.addMonth("dec");
								}

							}
							threeDVarAvgVertical.changeSelectMonths();
						} else if (parameterName.equals("scale")) {
							if (parameterValue.equals("2")) {
								threeDVarAvgVertical.setPressureLevelScale("2");
								threeDVarAvgVertical.setVariableScale("0");
							} else if (parameterValue.equals("0")) {
								threeDVarAvgVertical.setPressureLevelScale("0");
								threeDVarAvgVertical.setVariableScale("0");
							} else if (parameterValue.equals("1")) {
								threeDVarAvgVertical.setPressureLevelScale("0");
								threeDVarAvgVertical.setVariableScale("1");
							} else if (parameterValue.equals("3")) {
								threeDVarAvgVertical.setPressureLevelScale("2");
								threeDVarAvgVertical.setVariableScale("1");
							}

						}
					}
					threeDVarAvgVertical.setExecutionPurpose(response.path("purpose").textValue());
					threeDVarAvgVertical.setImage(response.path("plotUrl").textValue());
					threeDVarAvgVertical.setDataURL(response.path("dataUrl").textValue());
					return ok(views.threeDVariableAerageVerticalProfile.render(threeDVarAvgVertical));
			}else if (serviceName.equals("3-D-Variable-2-D-Slice")){ //"16")){
					for (int i = 0; i < responseConfigItems.size(); i++) {
						String parameterName = responseConfigItems.get(i).path("parameter").path("purpose").textValue();
						String parameterValue = responseConfigItems.get(i).path("value").textValue();
						if (parameterName.equals("model")) {
							threeDVar2DSlice.setDataSource(parameterValue);

						} else if (parameterName.equals("var")) {
							threeDVar2DSlice.setVariableName(parameterValue);

						} else if (parameterName.equals("pr")) {
							threeDVar2DSlice.setPressureLevel(parameterValue);
						} else if (parameterName.equals("startT")) {
							threeDVar2DSlice.setStartYearMonth(parameterValue);
						} else if (parameterName.equals("endT")) {
							threeDVar2DSlice.setEndYearMonth(parameterValue);
						} else if (parameterName.equals("lon1")) {
							threeDVar2DSlice.setStartLon(parameterValue);
						} else if (parameterName.equals("lon2")) {
							threeDVar2DSlice.setEndLon(parameterValue);
						} else if (parameterName.equals("lat1")) {
							threeDVar2DSlice.setStartLat(parameterValue);
						} else if (parameterName.equals("lat2")) {
							threeDVar2DSlice.setEndLat(parameterValue);
						} else if (parameterName.equals("months")) {
							String[] months = parameterValue.split(",");

							for (int j = 0; j < months.length; j++) {
								if (months[j].equals("1")) {
									threeDVar2DSlice.addMonth("jan");
								} else if (months[j].equals("2")) {
									threeDVar2DSlice.addMonth("feb");
								} else if (months[j].equals("3")) {
									threeDVar2DSlice.addMonth("mar");
								} else if (months[j].equals("4")) {
									threeDVar2DSlice.addMonth("apr");
								} else if (months[j].equals("5")) {
									threeDVar2DSlice.addMonth("may");
								} else if (months[j].equals("6")) {
									threeDVar2DSlice.addMonth("jun");
								} else if (months[j].equals("7")) {
									threeDVar2DSlice.addMonth("jul");
								} else if (months[j].equals("8")) {
									threeDVar2DSlice.addMonth("aug");
								} else if (months[j].equals("9")) {
									threeDVar2DSlice.addMonth("sep");
								} else if (months[j].equals("10")) {
									threeDVar2DSlice.addMonth("oct");
								} else if (months[j].equals("11")) {
									threeDVar2DSlice.addMonth("nov");
								} else if (months[j].equals("12")) {
									threeDVar2DSlice.addMonth("dec");
								}

							}
							threeDVar2DSlice.changeSelectMonths();
						} else if (parameterName.equals("scale")) {
							threeDVar2DSlice.setColorScale(parameterValue);

						}
					}
					threeDVar2DSlice.setExecutionPurpose(response.path("purpose").textValue());
					threeDVar2DSlice.setImage(response.path("plotUrl").textValue());
					threeDVar2DSlice.setDataURL(response.path("dataUrl").textValue());
					return ok(views.threeDVariableTwoDSlice.render(threeDVar2DSlice));

			}else{
				
			}

			// flash the response message
			Application.flashMsg(response);
			Application.flashMsg(response);

		}catch (IllegalStateException e) {
			e.printStackTrace();
			Application.flashMsg(RESTfulCalls
					.createResponse(ResponseType.CONVERSIONERROR));
		} catch (Exception e) {
			e.printStackTrace();
			Application.flashMsg(RESTfulCalls.createResponse(ResponseType.UNKNOWN));
		}
		Application.flashMsg(RESTfulCalls.createResponse(ResponseType.UNKNOWN));

		return ok();
	}
	
}
