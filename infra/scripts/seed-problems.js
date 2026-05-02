// Seed ~10 sample problems into app_db.problems.
//
// Usage:
//   mongosh "mongodb://localhost:27017/app_db" infra/scripts/seed-problems.js
//
// Idempotent — uses bulkWrite with upsert keyed on slug. Run again at any time
// to refresh problem text or add new ones without duplicating rows.

const SUPPORTED = [50, 54, 60, 62, 63, 71, 73];

const problems = [
  {
    slug: "two-sum",
    title: "Two Sum",
    difficulty: "EASY",
    description:
      "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`. You may assume each input has exactly one solution and you may not use the same element twice.",
    sampleTestCases: [
      { input: "[2,7,11,15]\n9", expectedOutput: "[0,1]", hidden: false },
      { input: "[3,2,4]\n6", expectedOutput: "[1,2]", hidden: false },
      { input: "[3,3]\n6", expectedOutput: "[0,1]", hidden: true },
    ],
  },
  {
    slug: "reverse-string",
    title: "Reverse String",
    difficulty: "EASY",
    description:
      "Write a function that reverses a string. The input string is given as an array of characters `s`. Modify the input array in-place with O(1) extra memory.",
    sampleTestCases: [
      { input: "hello", expectedOutput: "olleh", hidden: false },
      { input: "Hannah", expectedOutput: "hannaH", hidden: false },
    ],
  },
  {
    slug: "fizzbuzz",
    title: "FizzBuzz",
    difficulty: "EASY",
    description:
      "Given an integer `n`, print the numbers from 1 to n. For multiples of three print `Fizz`, for multiples of five print `Buzz`, and for multiples of both print `FizzBuzz`. Print one entry per line.",
    sampleTestCases: [
      { input: "5", expectedOutput: "1\n2\nFizz\n4\nBuzz", hidden: false },
      { input: "15", expectedOutput: "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz", hidden: true },
    ],
  },
  {
    slug: "valid-parentheses",
    title: "Valid Parentheses",
    difficulty: "EASY",
    description:
      "Given a string `s` containing just the characters '(', ')', '{', '}', '[' and ']', determine if the input string is valid. An input string is valid if open brackets are closed by the same type of brackets and in the correct order.",
    sampleTestCases: [
      { input: "()", expectedOutput: "true", hidden: false },
      { input: "()[]{}", expectedOutput: "true", hidden: false },
      { input: "(]", expectedOutput: "false", hidden: true },
    ],
  },
  {
    slug: "palindrome-number",
    title: "Palindrome Number",
    difficulty: "EASY",
    description:
      "Given an integer `x`, return `true` if `x` is a palindrome, and `false` otherwise. A palindrome reads the same forward and backward. Negative numbers are not palindromes.",
    sampleTestCases: [
      { input: "121", expectedOutput: "true", hidden: false },
      { input: "-121", expectedOutput: "false", hidden: false },
      { input: "10", expectedOutput: "false", hidden: true },
    ],
  },
  {
    slug: "longest-substring-without-repeating",
    title: "Longest Substring Without Repeating Characters",
    difficulty: "MEDIUM",
    description:
      "Given a string `s`, find the length of the longest substring without repeating characters.",
    sampleTestCases: [
      { input: "abcabcbb", expectedOutput: "3", hidden: false },
      { input: "bbbbb", expectedOutput: "1", hidden: false },
      { input: "pwwkew", expectedOutput: "3", hidden: true },
    ],
  },
  {
    slug: "merge-intervals",
    title: "Merge Intervals",
    difficulty: "MEDIUM",
    description:
      "Given an array of `intervals` where intervals[i] = [start, end], merge all overlapping intervals, and return an array of the non-overlapping intervals that cover all the intervals in the input.",
    sampleTestCases: [
      { input: "[[1,3],[2,6],[8,10],[15,18]]", expectedOutput: "[[1,6],[8,10],[15,18]]", hidden: false },
      { input: "[[1,4],[4,5]]", expectedOutput: "[[1,5]]", hidden: false },
    ],
  },
  {
    slug: "binary-search",
    title: "Binary Search",
    difficulty: "EASY",
    description:
      "Given a sorted array `nums` and an integer `target`, return the index where `target` is found, or -1 if it is not in the array. Solve in O(log n).",
    sampleTestCases: [
      { input: "[-1,0,3,5,9,12]\n9", expectedOutput: "4", hidden: false },
      { input: "[-1,0,3,5,9,12]\n2", expectedOutput: "-1", hidden: false },
    ],
  },
  {
    slug: "max-subarray",
    title: "Maximum Subarray",
    difficulty: "MEDIUM",
    description:
      "Given an integer array `nums`, find the contiguous subarray (containing at least one number) which has the largest sum and return its sum. Solve in O(n).",
    sampleTestCases: [
      { input: "[-2,1,-3,4,-1,2,1,-5,4]", expectedOutput: "6", hidden: false },
      { input: "[1]", expectedOutput: "1", hidden: false },
      { input: "[5,4,-1,7,8]", expectedOutput: "23", hidden: true },
    ],
  },
  {
    slug: "n-queens",
    title: "N-Queens",
    difficulty: "HARD",
    description:
      "The N-Queens puzzle is the problem of placing `n` queens on an n×n chessboard such that no two queens attack each other. Given an integer `n`, return the number of distinct solutions.",
    sampleTestCases: [
      { input: "4", expectedOutput: "2", hidden: false },
      { input: "1", expectedOutput: "1", hidden: false },
      { input: "8", expectedOutput: "92", hidden: true },
    ],
  },
];

const now = new Date();

const ops = problems.map((p) => ({
  updateOne: {
    filter: { slug: p.slug },
    update: {
      $set: {
        slug: p.slug,
        title: p.title,
        description: p.description,
        difficulty: p.difficulty,
        sampleTestCases: p.sampleTestCases,
        supportedLanguages: SUPPORTED,
      },
      $setOnInsert: { createdAt: now },
    },
    upsert: true,
  },
}));

const result = db.getSiblingDB("app_db").problems.bulkWrite(ops);
print(`Seeded problems: ${result.upsertedCount} inserted, ${result.modifiedCount} updated, ${result.matchedCount} matched.`);
