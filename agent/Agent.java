public class Agent {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java -cp agent.jar Agent <input_file> <output_file>");
            return;
        }

        String inputFile = args[0];
        String outputFile = args[1];
        SizeTester.main(new String[]{inputFile, outputFile});
    }
}
