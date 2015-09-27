package com.lethe_river.bloom;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 保持する要素の確認に{@link BloomFilter}を利用するSet.
 * 
 * 要素数の多いSetにおいて，結果が偽となるBloomFilterCheckSet同士のcontainsAll,equalsを多数行う場合に有効である．
 * このSetは他のSetにBloomFilterを追加して作られるが，元ととなるSetに関わらず要素の削除には対応していない．
 * 
 * @author YuyaAizawa
 *
 */
public class BloomFilterSet<E> implements Set<E> {
	
	private final Set<E> set;
	private final BloomFilter<E> filter;
	
	/**
	 * 指定したSetにBloomFilterを追加したSetを作成する．
	 * @param config BloomFilterの設定
	 * @param surpplier　SetのSupplier
	 */
	public BloomFilterSet (BloomConfig<E> config, Supplier<Set<E>> surpplier) {
		this.set = surpplier.get();
		this.filter = config.empty();
	}
	
	@Override
	public boolean add(E e) {
		filter.add(e);
		return set.add(e);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		for(E e : c) {
			filter.add(e);
		}
		return set.addAll(c);
	}
	
	@Override
	public void clear() {
		filter.clear();
		set.clear();
	}
	
	@Override
	public boolean contains(Object o) {
		
		@SuppressWarnings("unchecked")
		E element = (E) o;
		if(!filter.contains(element)) {
			return false;
		}
		
		return set.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if (c instanceof BloomFilterSet<?>) {
			@SuppressWarnings("unchecked")
			BloomFilterSet<E> bloomSet = (BloomFilterSet<E>) c;
			if(!filter.contains(bloomSet.filter)) {
				return false;
			}
		}
		return set.containsAll(c);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof BloomFilterSet<?>) {
			BloomFilterSet<?> other = (BloomFilterSet<?>) o;
			if(!filter.equals(other.filter)) {
				return false;
			}
		}
		return set.equals(o);
	}
	
	// 以下委譲
	
	@Override
	public void forEach(Consumer<? super E> action) {
		set.forEach(action);
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public Iterator<E> iterator() {
		Iterator<E> i = set.iterator();
		return new Iterator<E>() {

			@Override
			public boolean hasNext() {
				return i.hasNext();
			}

			@Override
			public E next() {
				return i.next();
			}
		};
	}

	@Override
	public Stream<E> parallelStream() {
		return set.parallelStream();
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public Spliterator<E> spliterator() {
		return set.spliterator();
	}

	@Override
	public Stream<E> stream() {
		return set.stream();
	}

	@Override
	public Object[] toArray() {
		return set.toArray();
	}
	
	@Override
	public <T> T[] toArray(T[] arg0) {
		return set.toArray(arg0);
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}
	
	// 以下テスト用のクラスに分離予定
	public static void main(String[] args) {
		
		// 要素数1000を1024byteのフィールドに突っ込んで衝突を確認する
		System.out.println("-- BloomFilter Test --");
		System.out.println("1000 elements, 2 hashes, 1024 bytes field");
		System.out.println();
		
		BloomConfig<String> config = BloomConfig.fromIntHash(String::hashCode, 2, 1024);
		BloomFilter<String> filter = config.empty();
		
		List<String> list1 = IntStream.rangeClosed(1, 1000).mapToObj(i -> getStrWithoutA()).collect(Collectors.toList());
		List<String> list2 = IntStream.rangeClosed(1, 1000).mapToObj(i -> getStrWithA()).collect(Collectors.toList());
		list1.forEach(s -> filter.add(s));
		
		if(list1.stream()
				.filter(s -> !filter.contains(s))
				.findAny().isPresent()) {
			throw new RuntimeException();
		};
		
		long fp = list2.stream()
				.filter(s -> filter.contains(s))
				.count();
		System.out.println("false positive "+fp+"/1000");
		System.out.println();
		
		
		
		// BloomFilterSetの測定
		long stime;
		long etime;
		long total1, total2, total3;
		
		Set<String> set1 = new TreeSet<>();
		Set<String> set2 = new BloomFilterSet<>(config, TreeSet::new);
		
		BloomConfig<String> config3 = BloomConfig.<String>withSHA256(2, 1024, s -> {
			byte[] b = s.getBytes();
			Byte[] c = new Byte[b.length];
			for(int i = 0;i < b.length;i++) {
				c[i] = b[i];
			}
			return c;
		}, new byte[]{1,2,3});
		Set<String> set3 = new BloomFilterSet<>(config3, TreeSet::new);
		
		Set<String> cset = new BloomFilterSet<>(config, TreeSet::new);
		cset.addAll(list1);
		Set<String> ncset = new BloomFilterSet<>(config, TreeSet::new);
		list1.stream()
				.limit(999)
				.forEach(s -> ncset.add(s));
		ncset.add("progA");
		
		Set<String> cset2 = new BloomFilterSet<>(config3, TreeSet::new);
		list1.stream()
				.limit(1000)
				.forEach(s -> cset2.add(s));
		Set<String> ncset2 = new BloomFilterSet<>(config3, TreeSet::new);
		list1.stream()
				.limit(999)
				.forEach(s -> ncset2.add(s));
		ncset2.add("progA");
		
		System.out.println("add x1000, clear");
		total1 = 0; total2 = 0; total3 = 0;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set1.clear();
			list1.forEach(s -> set1.add(s));
		}
		etime = System.currentTimeMillis();
		total1 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set2.clear();
			list1.forEach(s -> set2.add(s));
		}
		etime = System.currentTimeMillis();
		total2 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set3.clear();
			list1.forEach(s -> set3.add(s));
		}
		etime = System.currentTimeMillis();
		total3 = etime - stime;
		System.out.println("TreeSet                : "+(total1)+"us");
		System.out.println("TreeSet with Java hash : "+(total2)+"us");
		System.out.println("TreeSet with SHA256    : "+(total3)+"us");
		System.out.println();
		
		System.out.println("contains true x1000");
		total1 = 0; total2 = 0; total3 = 0;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			list1.forEach(s -> set1.contains(s));
		}
		etime = System.currentTimeMillis();
		total1 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			list1.forEach(s -> set2.contains(s));
		}
		etime = System.currentTimeMillis();
		total2 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			list1.forEach(s -> set3.contains(s));
		}
		etime = System.currentTimeMillis();
		total3 = etime - stime;
		System.out.println("TreeSet                : "+(total1)+"us");
		System.out.println("TreeSet with Java hash : "+(total2)+"us");
		System.out.println("TreeSet with SHA256    : "+(total3)+"us");
		System.out.println();
		
		System.out.println("contains false x1000");
		total1 = 0; total2 = 0; total3 = 0;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			list2.forEach(s -> set1.contains(s));
		}
		etime = System.currentTimeMillis();
		total1 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			list2.forEach(s -> set2.contains(s));
		}
		etime = System.currentTimeMillis();
		total2 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			list2.forEach(s -> set3.contains(s));
		}
		etime = System.currentTimeMillis();
		total3 = etime - stime;
		System.out.println("TreeSet                : "+(total1)+"us");
		System.out.println("TreeSet with Java hash : "+(total2)+"us");
		System.out.println("TreeSet with SHA256    : "+(total3)+"us");
		System.out.println();
		
		System.out.println("containsAll 1000 true");
		total1 = 0; total2 = 0; total3 = 0;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set1.containsAll(cset);
		}
		etime = System.currentTimeMillis();
		total1 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set2.containsAll(cset);
		}
		etime = System.currentTimeMillis();
		total2 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set3.containsAll(cset2);
		}
		etime = System.currentTimeMillis();
		total3 = etime - stime;
		System.out.println("TreeSet                : "+(total1)+"us");
		System.out.println("TreeSet with Java hash : "+(total2)+"us");
		System.out.println("TreeSet with SHA256    : "+(total3)+"us");
		System.out.println();
		
		System.out.println("containsAll 1000 false");
		total1 = 0; total2 = 0; total3 = 0;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set1.containsAll(ncset);
		}
		etime = System.currentTimeMillis();
		total1 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set2.containsAll(ncset);
		}
		etime = System.currentTimeMillis();
		total2 = etime - stime;
		stime = System.currentTimeMillis();
		for(int i = 0;i < 1000;i++) {
			set3.containsAll(ncset2);
		}
		etime = System.currentTimeMillis();
		total3 = etime - stime;
		System.out.println("TreeSet                : "+(total1)+"us");
		System.out.println("TreeSet with Java hash : "+(total2)+"us");
		System.out.println("TreeSet with SHA256    : "+(total3)+"us");
		System.out.println();
	}
	
	static String getStrWithoutA() {
		char[] chars = new char[5];
		chars[0] = (char)(Math.random() * 60 + 66);
		chars[1] = (char)(Math.random() * 60 + 66);
		chars[2] = (char)(Math.random() * 60 + 66);
		chars[3] = (char)(Math.random() * 60 + 66);
		chars[4] = (char)(Math.random() * 60 + 66);
		return new String(chars);
	}
	
	static String getStrWithA() {
		char[] chars = new char[5];
		int at = (int)(Math.random()*5);
		
		for(int i = 0;i < 5;i++) {
			chars[i] = at == i ? 'A' : (char)(Math.random() * 60 + 66);
		}
		return new String(chars);
	}
}
