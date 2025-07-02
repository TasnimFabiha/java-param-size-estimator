import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class SizeTester {

    private static Instrumentation instrumentation;

    // Called by the agent
    public static void setInstrumentation(Instrumentation inst) {
        instrumentation = inst;
    }

    // Resource: https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html
    private static final Map<String, Long> PRIMITIVE_SIZES = Map.of(
            "boolean", 1L,
            "byte", 1L,
            "char", 2L,
            "short", 2L,
            "int", 4L,
            "long", 8L,
            "float", 4L,
            "double", 8L
    );

    private static long getFallbackSize(String typeName) {
        if (PRIMITIVE_SIZES.containsKey(typeName)) return PRIMITIVE_SIZES.get(typeName);
        if (typeName.endsWith("[]")) {
            // Default size for array (header + 1 element assumed)
            return 16 + 8; // array overhead + pointer
        }
        return 64; // general object fallback
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
                Class<?> clazz = resolveType(typeName);
                if (clazz.isPrimitive()){
                    sizeMap.put(typeName, getFallbackSize(typeName));
                }
                else{
                    Object instance = createDummyInstance(clazz);
                    long size = instrumentation.getObjectSize(instance);
                    sizeMap.put(typeName, size);
                }
            } catch (Exception | Error e) {
                // e.printStackTrace();
                sizeMap.put(typeName, getFallbackSize(typeName)); // fallback size
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
            System.err.println(e);
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
        if (clazz.isArray())
            return java.lang.reflect.Array.newInstance(clazz.getComponentType(), 1);

        // Common interfaces with safe dummy values
        if (clazz == java.util.List.class) return new ArrayList<>();
        if (clazz == java.util.Set.class) return new HashSet<>();
        if (clazz == java.util.Map.class) return new HashMap<>();
        if (clazz == java.util.Queue.class) return new LinkedList<>();
        if (clazz == java.util.Collection.class) return new ArrayList<>();
        if (clazz == java.util.Optional.class) return Optional.empty();
        if (clazz == java.lang.Runnable.class) return (Runnable) () -> {};
       // if (clazz == java.util.function.Supplier.class) return (Supplier<Object>) () -> null;

        // Primitive wrappers
        if (clazz == boolean.class || clazz == Boolean.class) return Boolean.FALSE;
        if (clazz == byte.class || clazz == Byte.class) return (byte) 0;
        if (clazz == char.class || clazz == Character.class) return (char) 0;
        if (clazz == short.class || clazz == Short.class) return (short) 0;
        if (clazz == int.class || clazz == Integer.class) 
            return 0;
        if (clazz == long.class || clazz == Long.class) return 0L;
        if (clazz == float.class || clazz == Float.class) return 0f;
        if (clazz == double.class || clazz == Double.class) return 0d;

        if (clazz == String.class) return "";

        // Abstract or interface fallback
        if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()))
            return new Object();

        // Try to instantiate via no-arg constructor
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            return new Object();
        }
    }

    private static Class<?> resolveType(String typeName) throws ClassNotFoundException {
        return switch (typeName) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;

            case "boolean[]" -> boolean[].class;
            case "byte[]" -> byte[].class;
            case "char[]" -> char[].class;
            case "short[]" -> short[].class;
            case "int[]" -> int[].class;
            case "long[]" -> long[].class;
            case "float[]" -> float[].class;
            case "double[]" -> double[].class;

            case "java.lang.String[]" -> String[].class;

            default -> {
                if (typeName.endsWith("[]")) {
                    String baseType = typeName.substring(0, typeName.length() - 2);
                    Class<?> componentClass = resolveType(baseType);
                    yield java.lang.reflect.Array.newInstance(componentClass, 1).getClass();
                }
                yield Class.forName(typeName);
            }
        };
    }
}



