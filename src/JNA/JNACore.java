package JNA;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.util.List;

public class JNACore {

    private static JNACore instance = null;

    /*
     Access modes
     */
    public final int PROCESS_QUERY_INFORMATION = 0x0400;
    public final int PROCESS_VM_READ = 0x0010;
    public final int PROCESS_VM_WRITE = 0x0020;
    public final int PROCESS_VM_OPERATION = 0x0008;
    public final int PROCESS_ALL_ACCESS = 0x001F0FFF;


    /*
     Zezenia client objects
     */
    public int zezeniaPID;
    public Pointer zezeniaProcessHandle;
    private int[] processList = new int[512];
    private int[] dummyList = new int[512];
    private Memory pTemp = new Memory(8);
    private Memory toWrite = new Memory(1);
    private IntByReference bytesReturned = new IntByReference();

    /*
     JNACore Constructor
     */
    private JNACore() {
    }

    public static JNACore getInstance() {
        if (instance == null) {
            instance = new JNACore();
        }
        return instance;
    }

    /*
     Finds and sets the zezenia client's pid and a pointer to its process.
     */
    public void getFirstProcesses() {
        Psapi.INSTANCE.EnumProcesses(processList, 1024, dummyList);
        int pid;
        int i = 0;
        while (i < processList.length) {
            pid = processList[i];
            if (pid != 0) {
                Pointer ph = Kernel32.INSTANCE.OpenProcess(PROCESS_ALL_ACCESS, false, pid);
                if (ph != null) {
                    byte[] filename = new byte[512];
                    Psapi.INSTANCE.GetModuleBaseNameW(ph, new Pointer(0), filename, 512);
                    String test = new String(filename);
                    if (test.contains("Z e z e n i a . e x e")) {
                        zezeniaPID = pid;
                        zezeniaProcessHandle = ph;
                        return;
                    }
                    Kernel32.INSTANCE.CloseHandle(ph);
                }
            }
            i++;
        }
    }

    /*
     Returns a pointer to a process given by a pid.
     */
    public Pointer returnProcess(int pid) {
        Pointer process = Kernel32.INSTANCE.OpenProcess(PROCESS_ALL_ACCESS, false, pid);
        return process;
    }

    /*
     Reads the specified number of bytes in the specified memory location
     of the specified process.
     */
    public Memory readMemory(Pointer process, long address, int bytesToRead) {
        IntByReference read = new IntByReference(0);
        Memory output = new Memory(8);

        boolean ReadProcessMemory = Kernel32.INSTANCE.ReadProcessMemory(process, address, output, bytesToRead, read);
        return output;

    }

    /*
     Writes the specified number of bytes at the specified memory location
     of the specified process.
     */
    public void writeMemory(Pointer process, long address, byte[] data) {
        int size = data.length;

        //i have toWrite size set to 1 byte. if i need to write more than 1 in
        //the future, i will have to change this code.
        for (int i = 0; i < size; i++) {
            toWrite.setByte(i, data[i]);
        }
        IntByReference x = new IntByReference();
        Kernel32.INSTANCE.WriteProcessMemory(process, address, toWrite, size, x);
        if (x.getValue() < 4) {
        }
    }


    /*
     Returns address at the end of a given array of offsets using the base address.
    
     Use Example - readMemory(zezeniaPointer,findDynAddress(zezeniaPointer,xCoord,baseAddress),4)
     -will read the players xCoordinate from the zezenia client.
     */
    public long findDynAddress(int[] offsets, long baseAddress) {
        long address = baseAddress;
        long pointerAddress = 0;

        address = address + offsets[0];
        int i = 1;
        while (i < offsets.length) {
            if (i == 1) {
                boolean ReadProcessMemory = Kernel32.INSTANCE.ReadProcessMemory(zezeniaProcessHandle, address, pTemp, 4, bytesReturned);
            }
            pointerAddress = ((pTemp.getInt(0) + offsets[i]));
            if (i != offsets.length - 1) {
                boolean ReadProcessMemory = Kernel32.INSTANCE.ReadProcessMemory(zezeniaProcessHandle, pointerAddress, pTemp, 4, bytesReturned);
            }
            i++;
            //if pTempt returns 0, that means the value in memory isnt occupied yet
            if (pTemp.getInt(0) == 0) {
                return 0;
            }
        }
        return pointerAddress;
    }

    /*
     Returns the base address of the modules of the given process.
     I'm just using it for debugging.
     */
    public int getBaseAddress() {
        try {
            Pointer hProcess = zezeniaProcessHandle;

            List<JNA.Module> hModules = PsapiTools.getInstance().EnumProcessModules(hProcess);

            for (JNA.Module m : hModules) {

                System.out.println((m.getFileName() + ": entry point at - 0x" + Long.toHexString(Pointer.nativeValue(m.getEntryPoint()))));
                System.out.println("Base of dll : " + m.getLpBaseOfDll());
                System.out.println(Integer.valueOf("" + Pointer.nativeValue(m.getLpBaseOfDll())));

            }
        } catch (Exception e) {
            System.err.println("Something broke in getbaseaddress method");
            return -1;
        }
        return 0;
    }
}
