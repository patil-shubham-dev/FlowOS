package com.todo.dailyroutine.domain.vector

import java.util.*

class BertTokenizer(private val vocab: Map<String, Int>) {
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"

    fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val words = text.lowercase().split(Regex("\\s+"))
        
        for (word in words) {
            if (word.isEmpty()) continue
            
            var start = 0
            while (start < word.length) {
                var end = word.length
                var curSubword: String? = null
                
                while (start < end) {
                    var subword = word.substring(start, end)
                    if (start > 0) subword = "##$subword"
                    
                    if (vocab.containsKey(subword)) {
                        curSubword = subword
                        break
                    }
                    end--
                }
                
                if (curSubword == null) {
                    tokens.add(unkToken)
                    break
                } else {
                    tokens.add(curSubword)
                    start = end
                }
            }
        }
        return tokens
    }

    fun convertTokensToIds(tokens: List<String>, maxSeqLen: Int): IntArray {
        val ids = IntArray(maxSeqLen) { vocab[padToken] ?: 0 }
        val finalTokens = mutableListOf<String>()
        finalTokens.add(clsToken)
        finalTokens.addAll(tokens.take(maxSeqLen - 2))
        finalTokens.add(sepToken)
        
        for (i in finalTokens.indices) {
            ids[i] = vocab[finalTokens[i]] ?: vocab[unkToken] ?: 0
        }
        return ids
    }

    companion object {
        fun loadFromAssets(assets: android.content.res.AssetManager, fileName: String): BertTokenizer {
            val vocab = mutableMapOf<String, Int>()
            assets.open(fileName).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    vocab[line.trim()] = index
                }
            }
            return BertTokenizer(vocab)
        }
    }
}
