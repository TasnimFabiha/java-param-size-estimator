import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class SizeTester {

    private static Instrumentation instrumentation;

    // Called by the agent
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp agent.jar Agent <inputFile> <outputFile>");
            return;
        }
        String inputFile = args[0];
        String outputFile = args[1];

        List<String> typeNames = readLines(inputFile);
        Map<String, Long> sizeMap = new LinkedHashMap<>();

        for (String typeName : typeNames) {
            try {
                Class<?> clazz = Class.forName(typeName);
                Object instance = createDummyInstance(clazz);
                long size = instrumentation.getObjectSize(instance);
                sizeMap.put(typeName, size);
            } catch (Exception | Error e) {
                sizeMap.put(typeName, 64L); // fallback size
            }
        }

        writeCsv(sizeMap, outputFile);
        System.out.println("Finished writing to " + outputFile);
    }

    private static List<String> readLines(String fileName) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null)
                lines.add(line.trim());
        } catch (IOException e) {
            System.err.println("Failed to read " + fileName);
        }
        return lines;
    }

    private static void writeCsv(Map<String, Long> map, String fileName) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                pw.println(entry.getKey() + "," + entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Failed to write " + fileName);
        }
    }

    private static Object createDummyInstance(Class<?> clazz) throws Exception {
        if (clazz.isArray()) return java.lang.reflect.Array.newInstance(clazz.getComponentType(), 0);
        if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) return new Object();
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            return new Object();
        }
    }
}
