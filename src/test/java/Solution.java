import java.math.BigInteger;
import java.util.HashSet;

public class Solution {

	public static void main(String[] args) {
		System.out.println(Solution.solution(8));
	}

	static HashSet<HashSet<Integer>> seen;

	public static int solution(int n) {
		seen = new HashSet<>();
		int[] arr = new int[n - 1];

		for (int i = 1; i < n; i++) {
			arr[i - 1] = i;
		}

		split(new HashSet<>(), 0, n, arr);

		System.out.println(seen);

		return seen.size();
	}

	public static void split(HashSet<Integer> curr, int start, int target, int[] candidates) {
		if (target == 0) {
			seen.add(new HashSet<Integer>(curr));
			return;
		}
		if (target < 0) {
			return;
		}

		int prev = -1;
		for (int i = start; i < candidates.length; i++) {
			if (prev != candidates[i]) {
				curr.add(candidates[i]);
				split(curr, i + 1, target - candidates[i], candidates);
				curr.remove(curr.size() - 1);
				prev = candidates[i];
			}
		}
	}
}