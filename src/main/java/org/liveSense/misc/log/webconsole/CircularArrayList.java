package org.liveSense.misc.log.webconsole;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class CircularArrayList<M> extends AbstractList<M> implements List<M>, Serializable {
	private static final long serialVersionUID = 8385494549590136775L;

	private M[] elementData;
	// head points to the first logical element in the array, and
	// tail points to the element following the last.  This means
	// that the list is empty when head == tail.  It also means
	// that the elementData array has to have an extra space in it.
	private int head = 0, tail = 0;
	// Strictly speaking, we don't need to keep a handle to size,
	// as it can be calculated programmatically, but keeping it
	// makes the algorithms faster.
	private int size = 0;
	
	private int lastId = -1;
	private int maxCapacity = 10;
	
	Class<M> clazz;

	public CircularArrayList(Class<M> clazz) {
		this(clazz, 10);
	}

	private static <M>M[] createTypedArray(Class clazz, int size) {
            if (size < 1) return null;
            //M[] o = new M[]{size};
			M[] o = (M[]) Array.newInstance(clazz, size);
            return o;
	}
	
	@SuppressWarnings("unused")
	public M createBeanInstance() {
		try {
			return clazz.newInstance();
		} catch (InstantiationException e) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		}
	}
	
	public CircularArrayList(Class<M> clazz, int size) {
		elementData = (M[]) createTypedArray(clazz, size);
		maxCapacity = size;
	}

	public CircularArrayList(Collection<M> c) {
		tail = c.size();
		elementData = (M[]) createTypedArray(clazz, c.size());
		maxCapacity = c.size();
		c.toArray(elementData);
	}

	// The convert() method takes a logical index (as if head was
	// always 0) and calculates the index within elementData
	private int convert(int index) {
		return (index + head) % elementData.length;
	}

	public boolean isEmpty() {
		return head == tail; // or size == 0
	}

	// We use this method to ensure that the capacity of the
	// list will suffice for the number of elements we want to
	// insert.  If it is too small, we make a new, bigger array
	// and copy the old elements in.
	public void ensureCapacity(int minCapacity) {
		int oldCapacity = elementData.length;
		if (minCapacity > oldCapacity) {
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if (newCapacity < minCapacity)
				newCapacity = minCapacity;
			M newData[] = (M[]) createTypedArray(clazz, newCapacity);
			toArray(newData);
			tail = size;
			head = 0;
			elementData = newData;
		}
	}

	public int size() {
		// the size can also be worked out each time as:
		// (tail + elementData.length - head) % elementData.length
		return size;
	}

	public boolean contains(Object elem) {
		return indexOf(elem) >= 0;
	}

	public int indexOf(Object elem) {
		if (elem == null) {
			for (int i = 0; i < size; i++)
				if (elementData[convert(i)] == null)
					return i;
		} else {
			for (int i = 0; i < size; i++)
				if (elem.equals(elementData[convert(i)]))
					return i;
		}
		return -1;
	}

	public int lastIndexOf(Object elem) {
		if (elem == null) {
			for (int i = size - 1; i >= 0; i--)
				if (elementData[convert(i)] == null)
					return i;
		} else {
			for (int i = size - 1; i >= 0; i--)
				if (elem.equals(elementData[convert(i)]))
					return i;
		}
		return -1;
	}

	@Override
	public M[] toArray() {
		return (M[]) toArray(createTypedArray(clazz, size));
	}

	@SuppressWarnings("unchecked")
	public M[] toArray(Object a[]) {
		if (a.length < size)
			a = createTypedArray(clazz, size); //(M[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
		if (head < tail) {
			System.arraycopy(elementData, head, a, 0, tail - head);
		} else {
			System.arraycopy(elementData, head, a, 0, elementData.length - head);
			System.arraycopy(elementData, 0, a, elementData.length - head, tail);
		}
		if (a.length > size)
			a[size] = null;
		return (M[]) a;
	}

	private void rangeCheck(int index) {
		if (index >= size || index < 0)
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
	}

	public M get(int index) {
		rangeCheck(index);
		return (M) elementData[convert(index)];
	}

	public M set(int index, M element) {
		modCount++;
		rangeCheck(index);
		M oldValue = elementData[convert(index)];
		elementData[convert(index)] = element;
		return oldValue;
	}

	public boolean add(M o) {
		modCount++;
		// We have to have at least one empty space
		ensureCapacity(size + 1 + 1);
		elementData[tail] = o;
		tail = (tail + 1) % elementData.length;
		size++;
		lastId++;
		return true;
	}

	// This method is the main reason we re-wrote the class.
	// It is optimized for removing first and last elements
	// but also allows you to remove in the middle of the list.
	public M remove(int index) {
		modCount++;
		rangeCheck(index);
		int pos = convert(index);
		// an interesting application of try/finally is to avoid
		// having to use local variables
		try {
			return (M) elementData[pos];
		} finally {
			elementData[pos] = null; // Let gc do its work
			// optimized for FIFO access, i.e. adding to back and
			// removing from front
			if (pos == head) {
				head = (head + 1) % elementData.length;
			} else if (pos == tail) {
				tail = (tail - 1 + elementData.length) % elementData.length;
			} else {
				if (pos > head && pos > tail) { // tail/head/pos
					System.arraycopy(elementData, head, elementData, head + 1, pos - head);
					head = (head + 1) % elementData.length;
				} else {
					System.arraycopy(elementData, pos + 1, elementData, pos, tail - pos - 1);
					tail = (tail - 1 + elementData.length) % elementData.length;
				}
			}
			size--;
		}
	}

	public void clear() {
		modCount++;
		// Let gc do its work
		for (int i = head; i != tail; i = (i + 1) % elementData.length)
			elementData[i] = null;
		head = tail = size = 0;
	}

	public boolean addAll(Collection<? extends M> c) {
		modCount++;
		int numNew = c.size();
		
		if (numNew+size> maxCapacity) {
			for (int i=0; i<(numNew+size)-maxCapacity; i++) {
				remove(0);
			}
		}
		// We have to have at least one empty space
		ensureCapacity(size + numNew + 1);
		@SuppressWarnings("unchecked")
		Iterator<M> e = (Iterator<M>) c.iterator();
		for (int i = 0; i < numNew; i++) {
			elementData[tail] = e.next();
			tail = (tail + 1) % elementData.length;
			size++;
			if (size > maxCapacity) {
				for (int j=0; j<size-maxCapacity; j++) {
					remove(0);
				}
			}
			lastId++;
		}
		return numNew != 0;
	}

	public void add(int index, Object element) {
		throw new UnsupportedOperationException("This method left as an exercise to the reader ;-)");
	}

	public boolean addAll(int index, @SuppressWarnings("rawtypes") Collection c) {
		throw new UnsupportedOperationException("This method left as an exercise to the reader ;-)");
	}

	private synchronized void writeObject(ObjectOutputStream s) throws IOException {
		s.writeInt(size);
		for (int i = head; i != tail; i = (i + 1) % elementData.length)
			s.writeObject(elementData[i]);
	}

	@SuppressWarnings("unchecked")
	private synchronized void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
		// Read in size of list and allocate array
		head = 0;
		size = tail = s.readInt();
		elementData =(M[]) createTypedArray(clazz, tail) ;
		// Read in all elements in the proper order.
		for (int i = 0; i < tail; i++)
			elementData[i] = (M) s.readObject();
	}
	
	public M[] getFromId(int id) {
		if (id < lastId) {
			int s = lastId - id;
			if (s > size) s = size;
			int start = size-s;
			int end = start + s;
			M[] ret =  createTypedArray(clazz, s);
			for (int i = start; i<end; i++) {
				ret[i-start] = get(i);
			}
			return ret;
		} 			
		return createTypedArray(clazz, 0);
	}
	
	public int getLastId() {
		return lastId;
	}
}
