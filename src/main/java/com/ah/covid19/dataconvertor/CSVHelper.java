package com.ah.covid19.dataconvertor;

import com.ah.covid19.dataconvertor.model.Case;
import com.ah.covid19.dataconvertor.model.Location;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CSVHelper {
    private static final Logger logger = LogManager.getLogger(GoogleSheetHelper.class);

    private static final int COLUMN_PROVINCE = 0;
    private static final int COLUMN_COUNTRY = 1;
    private static final int COLUMN_LATITUDE = 2;
    private static final int COLUMN_LONGITUDE = 3;

    private static final int COLUMN_DAILY_REPORT_PROVINCE = 0;
    private static final int COLUMN_DAILY_REPORT_COUNTRY = 1;
    private static final int COLUMN_DAILY_REPORT_CONFIRMED = 3;
    private static final int COLUMN_DAILY_REPORT_DEATH = 4;
    private static final int COLUMN_DAILY_REPORT_RECOVERED = 5;

    public static Location getStandardizedLocation(final String country, final String province) {
        String countryName = StringUtils.trim(country);
        String provinceName = StringUtils.trim(province);

        switch (countryName) {
            case "UK":
                return new Location.LocationBuilder().country("United Kingdom").province("United Kingdom").build();
            case "Australia":
                if (StringUtils.isEmpty(provinceName))
                    return new Location.LocationBuilder().country("Australia").province("New South Wales").build();
            case "Denmark":
                if (StringUtils.isEmpty(provinceName))
                    return new Location.LocationBuilder().country("Denmark").province("Denmark").build();
            case "France":
                if (StringUtils.isEmpty(provinceName))
                    return new Location.LocationBuilder().country("France").province("France").build();
            case "Netherlands":
                if (StringUtils.isEmpty(provinceName))
                    return new Location.LocationBuilder().country("Netherlands").province("Netherlands").build();
            default:
                return new Location.LocationBuilder().country(countryName).province(provinceName).build();
        }
    }

    public static Map<Location, List<Case>> readDailyReportCSVFileToMap(final String dailyReportFileFolder, final Calendar startDate, final Calendar endDate, final String fileNameDatePattern) {
        final Map<Location, List<Case>> result = new TreeMap<>();
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(fileNameDatePattern);
        int numOfDays = 0;
        final List<Case> zeroCasesList = new ArrayList<>();

        while (endDate.compareTo(startDate) > 0) {
            String date = simpleDateFormat.format(startDate.getTime());
            File csvFile = new File(dailyReportFileFolder + "/" + date + ".csv");

            try (CSVReader csvReader = new CSVReader(new FileReader(csvFile))) {
                //skip header
                csvReader.readNext();

                //reading the daily report
                String[] parsedLine;
                while ((parsedLine = csvReader.readNext()) != null) {

                    Location location = getStandardizedLocation(parsedLine[COLUMN_DAILY_REPORT_COUNTRY], parsedLine[COLUMN_DAILY_REPORT_PROVINCE]);
                    int confirmedValue = StringUtils.isEmpty(parsedLine[COLUMN_DAILY_REPORT_CONFIRMED]) ? 0 : Integer.valueOf(parsedLine[COLUMN_DAILY_REPORT_CONFIRMED]);

                    if (result.containsKey(location)) {
                        //existing location found
                        Case previousDayCase = result.get(location).get(numOfDays-1);

                        Case confirmedCase = new Case.CaseBuilder()
                                .date(date)
                                .accumulatedCase(confirmedValue)
                                .dailyNewCase(confirmedValue - previousDayCase.getAccumulatedCase())
                                .build();

                        result.get(location).add(confirmedCase);
                    } else {
                        //new location found
                        Case confirmedCase = new Case.CaseBuilder()
                                .date(date)
                                .accumulatedCase(confirmedValue)
                                .dailyNewCase(confirmedValue)
                                .build();

                        List<Case> caseList = new ArrayList<>(zeroCasesList);
                        caseList.add(confirmedCase);
                        result.put(location, caseList);
                    }
                }
            } catch(IOException | CsvValidationException e) {
                e.printStackTrace();
                return null;
            }

            //append empty case for location without daily update
            appendCaseToLocationWithoutUpdate(result, numOfDays, date);

            zeroCasesList.add(new Case.CaseBuilder().date(date).accumulatedCase(0).build());
            startDate.add(Calendar.DAY_OF_MONTH, 1);
            numOfDays++;
        }
        return result;
    }

    public static Map<Location, List<Case>> appendCaseToLocationWithoutUpdate(final Map<Location, List<Case>> caseMap, final int numOfDays, final String date) {
        caseMap.values().stream().forEach(list -> {
            if (numOfDays < 1 && list.size() < numOfDays + 1) {
                list.add(new Case.CaseBuilder()
                        .date(date)
                        .accumulatedCase(list.get(numOfDays - 1).getAccumulatedCase())
                        .dailyNewCase(0)
                        .build());
            }
        });

        return caseMap;
    }

    public static Map<Location, List<Case>> readTimeSeriesCSVFileToMap(final File csvFile) {
        final Map<Location, List<Case>> result = new TreeMap<>();

        try (CSVReader csvReader = new CSVReader(new FileReader(csvFile))) {
            String[] parsedDateLine = csvReader.readNext();

            String[] parsedLine;
            while ((parsedLine = csvReader.readNext()) != null) {
                Location location = new Location.LocationBuilder()
                        .country(parsedLine[COLUMN_COUNTRY])
                        .province(parsedLine[COLUMN_PROVINCE])
                        .longitude(parsedLine[COLUMN_LONGITUDE])
                        .latitude(parsedLine[COLUMN_LATITUDE])
                        .build();

                List<Case> accumulatedCases = new LinkedList<>();
                for (int i = COLUMN_LONGITUDE +1; i<parsedLine.length-1; i++) {
                    accumulatedCases.add(new Case.CaseBuilder()
                            .date(parsedDateLine[i])
                            .accumulatedCase(Integer.valueOf(parsedLine[i]))
                            .build()
                    );
                }

                if (result.containsKey(location)) {
                    //TODO: to verify each value of both to try to correct the value
                    logger.error("Duplicated locaation found in csv " + location);;
                } else {
                    result.put(location, accumulatedCases);
                }
            }
        } catch(IOException | CsvValidationException e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }
}
