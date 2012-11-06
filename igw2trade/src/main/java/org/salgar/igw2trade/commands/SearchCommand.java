package org.salgar.igw2trade.commands;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.chain.Command;
import org.apache.commons.chain.Context;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

public class SearchCommand implements Command {
	private static final Logger log = Logger.getLogger(SearchCommand.class);

	@SuppressWarnings("unchecked")
	public boolean execute(Context context) throws Exception {
		DefaultHttpClient client = (DefaultHttpClient) context.get("client");
		CookieStore cookieStore = (CookieStore) context.get("cookiestore");
		String searchLevel = (String) context.get("searchlevel");
		int minimumAmountOfDemand = (Integer) context
				.get("minimum_amount_of_demand");
		int profitMarginAgainstVendor = (Integer) context
				.get("profit_margin_against_vendor_value");
		int operationBracketTop = (Integer) context
				.get("operation_bracket_top");
		int operationBracketBottom = (Integer) context
				.get("operation_bracket_bottom");

		String result = getItemList(client, cookieStore, 1, searchLevel);
		int total = findTotalResults(result);
		List<String> worthyObjects = new ArrayList<String>();
		analyzeWorthyObjects(result, worthyObjects, minimumAmountOfDemand,
				profitMarginAgainstVendor, operationBracketTop,
				operationBracketBottom);

		for (int i = 11, n = total; i < n; i += 10) {
			result = getItemList(client, cookieStore, i, searchLevel);
			analyzeWorthyObjects(result, worthyObjects, minimumAmountOfDemand,
					profitMarginAgainstVendor, operationBracketTop,
					operationBracketBottom);
		}

		context.put("worthAnalyzing", worthyObjects);
		return false;
	}

	@SuppressWarnings("unused")
	private String getItemList(DefaultHttpClient client,
			CookieStore cookieStore, int offset, String searchLevel)
			throws ClientProtocolException, IOException {

		HttpGet get = new HttpGet(
				"https://tradingpost-live.ncplatform.net/ws/search.json?text="
				/* + URLEncoder.encode("Eye of Power Scepter", "UTF-8") */
				+ "&offset=" + offset + "&levelmin=" + searchLevel
						+ "&levelmax=" + searchLevel);

		// HttpGet get = new
		// HttpGet("https://tradingpost-live.ncplatform.net/ws/search.json?ids=13716&levelmin=56&levelmax=64");

		// HttpGet get = new
		// HttpGet("https://tradingpost-live.ncplatform.net/ws/listings.json?id=13716&type=all");

		get.setHeader("X-Requested-With", "XMLHttpRequest");
		get.addHeader("Cookie", "s="
				+ cookieStore.getCookies().get(0).getValue());
		HttpResponse responseGet = client.execute(get);
		List<Cookie> cookies = client.getCookieStore().getCookies();

		HttpEntity entity = responseGet.getEntity();

		BufferedInputStream bis = new BufferedInputStream(entity.getContent());
		int length = 0;
		byte[] buff = new byte[1024];
		StringBuffer sb = new StringBuffer(1024);
		while ((length = bis.read(buff)) != -1) {
			sb.append(new String(buff, 0, length, "UTF-8"));
		}

		String result = sb.toString();
		log.info("Got the list!");
		log.info(result);

		return result;
	}

	private int findTotalResults(String result) {
		String pattern = "\"total\":\"";
		int start = result.indexOf(pattern);
		String total = result.substring(start + pattern.length(),
				result.indexOf("\"", start + pattern.length()));

		return Integer.valueOf(total);
	}

	private void analyzeWorthyObjects(String result,
			List<String> worthyObjects, int minimumAmountOfDemand,
			int profitMarginAgainstVendor, int operationBracketTop,
			int operationBracketBottom) {
		int i = 0;

		while ((i = result.indexOf("{\"type_id", i)) > -1) {
			int end = result.indexOf("}", i + 1);
			String object = result.substring(i, end + 1);
			String[] fields = object.split(",");
			int countValue = 0;
			int priceValue = 0;
			int vendorValue = 0;
			for (int j = 0, n = fields.length; j < n; j++) {

				if (fields[j].indexOf("count") > -1) {
					String[] count = fields[j].split(":");

					countValue = Integer.valueOf(count[1].substring(1,
							count[1].length() - 1));
				} else if (fields[j].indexOf("price") > -1) {
					String[] price = fields[j].split(":");

					priceValue = Integer.valueOf(price[1].substring(1,
							price[1].length() - 1));
				} else if (fields[j].indexOf("vendor") > -1) {
					String[] vendor = fields[j].split(":");

					vendorValue = Integer.valueOf(vendor[1].substring(1,
							vendor[1].length() - 1));
				}
			}
			if (countValue >= minimumAmountOfDemand
					&& priceValue > profitMarginAgainstVendor * vendorValue
					&& (priceValue <= operationBracketTop && priceValue >= operationBracketBottom)) {
				log.info(object);
				worthyObjects.add(object);
			}
			i = end;
		}
	}

}