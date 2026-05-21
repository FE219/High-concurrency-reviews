import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.*;

public class Main{


    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line = br.readLine();

        int n = Integer.parseInt(line.trim());
        String[] arr = new String[n];

        HashSet<String> prefixSet = new HashSet<>();
        HashSet<String> suffixSet = new HashSet<>();

        for(int i=0; i<n; i++){
            arr[i] = br.readLine().trim();
            String s = arr[i];
            int len = s.length();

            for(int j=1; j<len; j++){
                prefixSet.add(s.substring(0, j));
                suffixSet.add(s.substring(len-j));
            }
        }

        ArrayList<String> ans = new ArrayList<>();

        for(int i=0; i<n; i++){
            String s = arr[i];
            int len = s.length();
            boolean isValid = false;

            for(int j=1; j<len; j++){
                String left = s.substring(0, j);
                String right = s.substring(j);

                if(suffixSet.contains(left) && prefixSet.contains(right)){
                    isValid = true;
                    break;
                }
            }

            if(isValid){
                ans.add(s);
            }
        }

        Collections.sort(ans);

        System.out.println(ans.size());
        for(String s: ans){
            System.out.println(s);
        }
    }
}