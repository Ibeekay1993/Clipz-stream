import unittest

from backend.main import Chunk, fill_requested_chunks, semantic_chunks


class ClipSelectionTests(unittest.TestCase):
    def test_missing_candidates_do_not_create_fake_clips_or_captions(self):
        selected = fill_requested_chunks([], 3, candidates=[])

        self.assertEqual(selected, [])

    def test_selection_returns_only_real_candidates_when_count_is_short(self):
        words = [
            {"word": "A", "startMs": 0, "endMs": 500},
            {"word": "real", "startMs": 500, "endMs": 1000},
            {"word": "sentence.", "startMs": 1000, "endMs": 1500},
        ]
        candidate = Chunk(
            start=0.0,
            end=1.5,
            text="A real sentence.",
            words=words,
            viable=True,
        )

        selected = fill_requested_chunks([], 3, candidates=[candidate])

        self.assertEqual(len(selected), 1)
        self.assertEqual(selected[0][0].words, words)
        self.assertNotIn("Clip Moment", selected[0][0].text)

    def test_short_transcript_chunk_keeps_its_real_words(self):
        words = [
            {"word": f"word{i}", "startMs": i * 1000, "endMs": i * 1000 + 700}
            for i in range(12)
        ]

        chunks = semantic_chunks(words, min_sec=28.0, max_sec=60.0)

        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0].words, words)
        self.assertEqual(chunks[0].text, " ".join(word["word"] for word in words))


if __name__ == "__main__":
    unittest.main()
