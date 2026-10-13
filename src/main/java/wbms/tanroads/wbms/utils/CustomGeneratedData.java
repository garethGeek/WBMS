package wbms.tanroads.wbms.utils;


import com.fasterxml.uuid.Generators;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeanUtils;

import java.io.File;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class CustomGeneratedData {
    public static String GenerateUniqueID() {
        UUID uuid = Generators.timeBasedGenerator().generate();
        String uuidStr = uuid.toString();//.replace("-", "");
        return uuidStr;
    }

    public static long getTimeStamp() {

        return new Date().getTime();
    }

    public static LocalDate stringToLocalDate(String formDate) {
        LocalDate convertedDate = LocalDate.parse(formDate);
        return convertedDate;
    }

    public static LocalDate convertToLocalDateViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static LocalDateTime convertToLocalDateTimeViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static LocalDateTime convertToLocalDateTimeViaMilisecond(Date dateToConvert) {
        return Instant.ofEpochMilli(dateToConvert.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static LocalDateTime getLocalDate(long valueToConvert) {
        Date currentDate = new Date(valueToConvert);
        return currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static LocalDate stringToDate(String dateToConvert) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return LocalDate.parse(dateToConvert, formatter);
    }

    public static LocalDate stringToLocalDateFormat(String dateToConvert) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return LocalDate.parse(dateToConvert, formatter);
    }

    public static LocalDate stringToDateFormat(String dateToConvert) throws ParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[dd-MM-yyyy][yyyy-MM-dd]");
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        LocalDate receivedDate = LocalDate.parse(dateToConvert, formatter);
        String returnDateStr = receivedDate.format(formatter2);

        return LocalDate.parse(returnDateStr, formatter2);
    }

    public static List<?> convertObjectToList(Object obj) {
        List<?> list = new ArrayList<>();
        if (obj.getClass().isArray()) {
            list = Arrays.asList((Object[]) obj);
        } else if (obj instanceof Collection) {
            list = new ArrayList<>((Collection<?>) obj);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static boolean isObjectHashMap(Object source) {

        try {
            HashMap<String, Object> result = (HashMap<String, Object>) source;
            return true;
        } catch (ClassCastException e) {
        }

        return false;

    }

    public static boolean isObjectString(Object source) {

        try {
            //String result = (String) source;
            return true;
        } catch (ClassCastException e) {
        }

        return false;

    }

    public static String harshMethod(String string) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(string.getBytes());

        byte[] byteData = md.digest();

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xFF) + 256, 16).substring(1));
        }
        return sb.toString();
    }

    public static String convertFileToBase64(File file) {
        String contentToSend = null;
        try {
            if (file.exists()) {
                byte[] fileContent = FileUtils.readFileToByteArray(file);
                contentToSend = Base64.getEncoder().encodeToString(fileContent);
            }else {
                contentToSend = Constant.FILE_NOT_EXIST.toString();
            }
        }catch (Exception e) {
            //logger.error("Error ocured while accessing the file",e);
            System.out.println("Error ocured while accessing the file");
        }
        return contentToSend;
    }

    //method to map an entity class into a dto class
    public static <E, D> D mapToDTO(E entity, Class<D> dtoClass) {
        try {
            D dto = dtoClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(entity, dto);
            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Error while mapping entity to DTO", e);
        }
    }


    //method to map a List of an entity class into a List of a dto class

    public static <E, D> List<D> mapListToDTO(List<E> entityList, Class<D> dtoClass) {
        // Map each entity to a DTO and collect the results into a list
        return entityList.stream()
                .map(entity -> mapToDTO(entity, dtoClass))
                .collect(Collectors.toList());
    }

    public static Date parseDate(String dateStr) throws ParseException {
        String dateFormatAllowed = "yyyy-MM-dd";
        SimpleDateFormat date = new SimpleDateFormat(dateFormatAllowed);
        return date.parse(dateStr);
    }



}
