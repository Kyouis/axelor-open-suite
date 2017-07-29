package com.axelor.apps.hr.service.expense;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.client.utils.URIBuilder;

import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.db.repo.ExpenseLineRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.google.common.base.Strings;
import com.google.inject.Inject;

import wslite.json.JSONException;
import wslite.json.JSONObject;

public class KilometricAllowanceServiceImpl implements KilometricAllowanceService {

	private String googleMapsApiKey;

	@Inject
	public KilometricAllowanceServiceImpl(AppHumanResourceService appHumanResourceService) {
		googleMapsApiKey = appHumanResourceService.getAppExpense().getGoogleMapsApiKey();
	}

	@Override
	public BigDecimal computeDistance(ExpenseLine expenseLine) throws AxelorException {
		BigDecimal distance = computeDistance(expenseLine.getFromCity(), expenseLine.getToCity());

		if (expenseLine.getKilometricTypeSelect().equals(ExpenseLineRepository.KILOMETRIC_TYPE_ROUND_TRIP)) {
			distance = distance.multiply(new BigDecimal(2));
		}

		return distance;
	}

	/**
	 * Compute the distance between two cities.
	 * 
	 * @param fromCity
	 * @param toCity
	 * @return
	 * @throws AxelorException
	 */
	private BigDecimal computeDistance(String fromCity, String toCity) throws AxelorException {
		try {
			User user = AuthUtils.getUser();
			JSONObject json = getGoogleMapsDistanceMatrixResponse(fromCity, toCity, user.getLanguage());
			String status = json.getString("status");

			if (status.equals("OK")) {
				JSONObject response = json.getJSONArray("rows").getJSONObject(0).getJSONArray("elements")
						.getJSONObject(0);
				status = response.getString("status");
				if (status.equals("OK")) {
					return new BigDecimal(response.getJSONObject("distance").getDouble("value") / 1000.);
				}
			}

			String msg = json.has("error_message") ? String.format("%s / %s", status, json.getString("error_message"))
					: status;

			throw new AxelorException(String.format(IExceptionMessage.KILOMETRIC_ALLOWANCE_GOOGLE_MAPS_ERROR, msg),
					IException.CONFIGURATION_ERROR);

		} catch (URISyntaxException | IOException | JSONException e) {
			throw new AxelorException(e, IException.CONFIGURATION_ERROR);
		}
	}

	/**
	 * Get JSON response from Google Maps Distance Matrix API.
	 * 
	 * @param origins
	 * @param destinations
	 * @param language
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws JSONException
	 */
	private JSONObject getGoogleMapsDistanceMatrixResponse(String origins, String destinations, String language)
			throws URISyntaxException, IOException, JSONException {

		URIBuilder ub = new URIBuilder("https://maps.googleapis.com/maps/api/distancematrix/json");
		ub.addParameter("origins", origins);
		ub.addParameter("destinations", destinations);
		ub.addParameter("language", language);

		if (!Strings.isNullOrEmpty(googleMapsApiKey)) {
			ub.addParameter("key", googleMapsApiKey);
		}

		URL url = new URL(ub.toString());
		URLConnection connection = url.openConnection();
		StringBuilder sb = new StringBuilder();

		try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				sb.append(inputLine + "\n");
			}
		}

		return new JSONObject(sb.toString());
	}

}
