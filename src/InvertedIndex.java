import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;

public class InvertedIndex {
    private static Set<String> links = new HashSet<>();

    public static void main(String[] args) throws IOException {
        // Step 1: Read 10 text files
        String[] fileNames = {
                "files/Doc1.txt", "files/Doc2.txt",
                "files/Doc3.txt", "files/Doc4.txt", "files/Doc5.txt",
                "files/Doc6.txt", "files/Doc7.txt", "files/Doc8.txt",
                "files/Doc9.txt", "files/Doc10.txt"
                // Add the remaining file names here
        };

        // Step 2: Build the inverted index for the files
        HashMap<String, DictEntry> index = buildIndex(fileNames);

        try (Scanner scanner = new Scanner(System.in)) {
            // Step 3: Read a query
            System.out.print("Enter a query: ");
            String query = scanner.nextLine().toLowerCase();

            // Step 4: Compute cosine similarity between the query and each file
            List<FileSimilarity> fileSimilarities = computeSimilarities(index, query, fileNames);

            // Step 5: Rank the files according to cosine similarity
            Collections.sort(fileSimilarities, Collections.reverseOrder());

            // Print the ranked files
            System.out.println("Ranked Files:");
            for (FileSimilarity fileSimilarity : fileSimilarities) {
                System.out.println(fileSimilarity.getFileName() + " - Cosine Similarity: " + fileSimilarity.getSimilarity());

            }
            String url = "https://www.google.com/"; // Replace with the URL you want to crawl
            System.out.println("Crawling URL: " + url);
            getPageLinks(url,15);
        }
    }

    // Builds the inverted index for the given list of file names
    // Builds the inverted index for the given list of file names
    public static HashMap<String, DictEntry> buildIndex(String[] fileNames) throws IOException {
        HashMap<String, DictEntry> index = new HashMap<>();
        int totalFiles = fileNames.length;

        for (String fileName : fileNames) {
            Set<String> uniqueTerms = new HashSet<>(); // Track unique terms in each file

            try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
                String line;
                int position = 0;
                while ((line = reader.readLine()) != null) {
                    String[] terms = line.split("\\W+");
                    for (String term : terms) {
                        term = term.toLowerCase();
                        DictEntry entry = index.get(term);
                        if (entry == null) {
                            entry = new DictEntry();
                            index.put(term, entry);
                        }
                        entry.term_freq++;
                        if (!entry.files.contains(fileName)) {
                            entry.files.add(fileName);
                            entry.doc_freq++;
                        }
                        List<Integer> positions = entry.positions.getOrDefault(fileName, new ArrayList<>());
                        positions.add(position);
                        entry.positions.put(fileName, positions);
                        position++;

                        uniqueTerms.add(term); // Add term to unique terms in the file
                    }
                }
            }

            // Update IDF for terms in the current file
            for (String term : uniqueTerms) {
                DictEntry entry = index.get(term);
                if (entry != null) {
                    double idf = Math.log((double) totalFiles / entry.doc_freq);
                    entry.idf = idf;
                }
            }
        }

        // Calculate and print TF-IDF for each term and document
        for (Map.Entry<String, DictEntry> entry : index.entrySet()) {
            String term = entry.getKey();
            DictEntry dictEntry = entry.getValue();

            System.out.println("Term: " + term);

            for (String fileName : dictEntry.files) {
                List<Integer> positions = dictEntry.positions.get(fileName);
                int termFrequency = positions.size();
                double tfidf = termFrequency * dictEntry.idf;

                System.out.println("Document: " + fileName);
                System.out.println("Term Frequency: " + termFrequency);
                System.out.println("TF-IDF: " + tfidf);
                System.out.println("-------------------");
            }
        }

        return index;
    }
    // Computes the cosine similarity between the query and each file
    public static List<FileSimilarity> computeSimilarities(HashMap<String, DictEntry> index, String query, String[] fileNames) {
        List<FileSimilarity> fileSimilarities = new ArrayList<>();
        System.out.println("IDF values:");

        for (String fileName : fileNames) {
            try {
                double similarity = calculateCosineSimilarity(query, fileName, index);
                fileSimilarities.add(new FileSimilarity(fileName, similarity));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Print IDF values
//        for (Map.Entry<String, DictEntry> entry : index.entrySet()) {
//            System.out.println(entry.getKey() + " - IDF: " + entry.getValue().idf);
//        }

        return fileSimilarities;
    }


    // Calculates the cosine similarity between the query and a file based on term frequency
    public static double calculateCosineSimilarity(String query, String fileName, HashMap<String, DictEntry> index) throws IOException {
        int queryDocCounter = 0, docCounter = 0;
        int counter = 0;
        double sumQuery = 0, sumDoc = 0, sum = 0;

        ArrayList<String> queryArr = splitSentence(query);
        ArrayList<String> docArr = splitWord(readFile(fileName));
        HashSet<String> allWords = new HashSet<>(queryArr);
        allWords.addAll(docArr);

        for (String word : allWords) {
            for (String w : queryArr) {
                if (word.equals(w))
                    queryDocCounter++;
            }
            for (String w : docArr) {
                if (word.equals(w))
                    docCounter++;
            }
            sumQuery += Math.pow(queryDocCounter, 2.0);
            sumDoc += Math.pow(docCounter, 2.0);
            counter = queryDocCounter * docCounter;
            sum += counter;
            queryDocCounter = docCounter = 0;
        }

        sumQuery = Math.sqrt(sumQuery);
        sumDoc = Math.sqrt(sumDoc);

        double cosineSimilarity = sum / (sumQuery * sumDoc);
        cosineSimilarity = valuePrecision(cosineSimilarity);

        return cosineSimilarity;
    }

    // Splits a sentence into an array of words
    public static ArrayList<String> splitSentence(String sentence) {
        ArrayList<String> words = new ArrayList<>();
        String[] terms = sentence.split("\\W+");
        for (String term : terms) {
            if (!term.isEmpty()) {
                words.add(term.toLowerCase());
            }
        }
        return words;
    }

    // Splits the input string into an array of words
    public static ArrayList<String> splitWord(String input) {
        ArrayList<String> words = new ArrayList<>();
        String[] terms = input.split("\\W+");
        for (String term : terms) {
            if (!term.isEmpty()) {
                words.add(term.toLowerCase());
            }
        }
        return words;
    }

    // Reads the contents of a file and returns it as a string
    public static String readFile(String fileName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(" ");
            }
            return sb.toString().trim();
        }
    }

    // Rounds the value to 2 decimal places
    public static double valuePrecision(double value) {
        return Math.round(value * 10000000.0) / 10000000.0;
    }// Crawls the URLs and adds them to the index
    // Crawls the URLs and adds them to the index
    public static void getPageLinks(String URL, int depth) {
        // Check if the depth is greater than 0
       /* if (depth > 0) {*/
            // Check if you have already crawled the URLs (we are intentionally not checking for duplicate content in this example)
            if (!links.contains(URL)) {
                try {
                    // If not, add it to the index
                    if (links.add(URL)) {
                        System.out.println(URL);
                    }

                    // Fetch the HTML code
                    Document document = Jsoup.connect(URL).get();

                    // Parse the HTML to extract links to other URLs
                    Elements linksOnPage = document.select("a[href]");

                    // For each extracted URL, recursively call the function with depth - 1
                    for (Element page : linksOnPage) {
                        getPageLinks(page.attr("abs:href"), depth - 1);
                    }
                } catch (IOException e) {
                    System.err.println("For '" + URL + "': " + e.getMessage());
                }
            }
        }
    }



// Entry in the index dictionary
class DictEntry {
    public int doc_freq = 0;
    public int term_freq = 0;
    public Set<String> files = new HashSet<>();
    public Map<String, List<Integer>> positions = new HashMap<>();
    public double idf = 0; // IDF field

    // Rest of the code...
}


// Represents the similarity between a file and the query
class FileSimilarity implements Comparable<FileSimilarity> {
    private String fileName;
    private double similarity;

    public FileSimilarity(String fileName, double similarity) {
        this.fileName = fileName;
        this.similarity = similarity;
    }

    public String getFileName() {
        return fileName;
    }

    public double getSimilarity() {
        return similarity;
    }

    @Override
    public int compareTo(@NotNull FileSimilarity other) {
        return Double.compare(this.similarity, other.similarity);
    }
}