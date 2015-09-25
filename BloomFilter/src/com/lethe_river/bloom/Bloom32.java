package com.lethe_river.bloom;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;


/**
 * int値で表現されるBloom filterを生成するクラス.
 * 
 * @see Bloom64
 * @see BloomConfig
 * @author YuyaAizawa
 *
 * @param <E> 要素の型
 */
public final class Bloom32<E> {
	// 32bitのフィルターにフラグを立てるにはhashは5bitで十分
	private static final int HASH_LENGTH = 5;
	private static final int HASH_FILTER = (1 << HASH_LENGTH)-1;
	
	private final Function<E, Integer> hashFunction;
	private final int k;
	
	/**
	 * 利用するハッシュ関数の数と元となるハッシュ関数を指定してBloom filterの生成器をつくる．
	 * int値を返すハッシュ関数からは最大6つのハッシュ値を利用できる．
	 * 元となるハッシュ関数から得たint値のハッシュは，内部で5bit毎に分割されて利用される.
	 * 
	 * @param k 利用するハッシュ関数の数(1~6)
	 * @param hashFunction 元となるハッシュ関数
	 */
	public Bloom32(int k, Function<E, Integer> hashFunction) {
		if(!(1 <= k && k <= Integer.SIZE/HASH_LENGTH)) {
			throw new IllegalArgumentException();
		}
		this.k = k;
		this.hashFunction = hashFunction;
	}
	
	/**
	 * 利用するハッシュ関数の数を指定してBloom filterの生成器を作る．
	 * 元となるハッシュ関数としてhashCode()を利用する．
	 * 
	 * @param k 利用するハッシュ関数の数(1~5)
	 */
	public Bloom32(int k) {
		this(k, null);
	}
	
	/**
	 * Bloom filterを返す．
	 * @param object フィルターするオブジェクト
	 * @return objectから計算されたBloom filter
	 */
	public int getFilter(E object) {
		int ret = 0;
		int hash = hashFunction == null ? object.hashCode() : hashFunction.apply(object);
		for(int i = 0; i < k;i++) {
			ret |= 1 << (hash & HASH_FILTER);
			hash >>= HASH_LENGTH;
		}
		return ret;
	}
	
	/**
	 * Bloom filterを返す．
	 * @param objects フィルターするオブジェクト
	 * @return objectsから計算されたBloom filter
	 */
	@SafeVarargs
	public final int getFilter(E... objects) {
		return Arrays.stream(objects)
				.map(t -> getFilter(t))
				.reduce(0, (l,r) -> l | r);
	}
	
	/**
	 * Bloom filterを返す．
	 * @param collection フィルターするオブジェクトを含むコレクション
	 * @return collectionから計算されたBloom filter
	 */
	public final int getFilter(Collection<E> collection) {
		return collection.stream()
				.map(t -> getFilter(t))
				.reduce(0, (l,r) -> l | r);
	}
	
	/**
	 * Bloom filter間の包含関係を判定する．
	 * @param sup 包含するBloom filter
	 * @param sub 包含されるBloom filter
	 * @return 包含すればtrue
	 */
	public static boolean isContained(int sup, int sub) {
		return ((sup & sub) ^ sub) == 0;
	}
	
	public static void main(String[] args) {
		Bloom32<String> bloom = new Bloom32<>(2);
		
		String str = new String("abc");
		
		int filterAbcDef = bloom.getFilter("abc", "def");
		int filterAbc    = bloom.getFilter("abc");
		
		System.out.println("abc:"+toBinaryStringZeroFill(bloom.getFilter("abc")));
		System.out.println("def:"+toBinaryStringZeroFill(bloom.getFilter("def")));
		System.out.println("ghi:"+toBinaryStringZeroFill(bloom.getFilter("ghi")));
		System.out.println();
		System.out.println("[abc, def]"+(Bloom32.isContained(filterAbcDef, bloom.getFilter(str)) ? " may contain" : " does not contain") + " [abc]");
		System.out.println("[abc]"+(Bloom32.isContained(filterAbc,bloom.getFilter("def")) ? " may contain" : " does not contain") + " [def]");
	}
	
	static String toBinaryStringZeroFill(int i) {
		String notFilled = Integer.toBinaryString(i);
		StringBuilder sb = new StringBuilder();
		for(int j = 32 - notFilled.length();j > 0;j--) {
			sb.append("0");
		}
		return sb.toString()+notFilled;
	}
}
