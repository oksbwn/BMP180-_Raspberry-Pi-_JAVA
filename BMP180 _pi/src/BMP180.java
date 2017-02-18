import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import java.lang.Math;
import java.nio.ByteBuffer;

public class BMP180
{
	private static int OVER_SAMPLING_RATE=1; //Define oversampling rate for Pressure Measurement. 
	static ByteBuffer buffer;
	@SuppressWarnings("static-access")
	public static void main(String args[]) throws Exception
	{

		I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);
		I2CDevice device = bus.getDevice(0x77);

		byte[] data = new byte[22];
		
		//Read Calibration data.
		device.read(0xAA, data, 0, 22);

		//Calculate Calibration factors
		int AC1 = buffer.wrap(new byte[]{data[0],data[1]}).getShort(); //Short
		int AC2 =buffer.wrap(new byte[]{data[2],data[3]}).getShort();
		int AC3 = buffer.wrap(new byte[]{data[4],data[5]}).getShort();
		int AC4 = buffer.wrap(new byte[]{0,0,data[6],data[7]}).getInt(); //Unsigned short
		int AC5 = buffer.wrap(new byte[]{0,0,data[8],data[9]}).getInt();
		int AC6 =buffer.wrap(new byte[]{0,0,data[10],data[11]}).getInt();
		int B1 = buffer.wrap(new byte[]{data[12],data[13]}).getShort();
		int B2 = buffer.wrap(new byte[]{data[14],data[15]}).getShort();
		//int MB = buffer.wrap(new byte[]{data[16],data[17]}).getShort();
		int MC = buffer.wrap(new byte[]{data[18],data[19]}).getShort();
		int MD = buffer.wrap(new byte[]{data[20],data[21]}).getShort();

		Thread.sleep(500);
		
		//Read uncompensated temperature
		device.write(0xF4, (byte)0x2E);
		Thread.sleep(100);
		device.read(0xF6, data, 0, 2);
		int unCompenstaedTemperature =  buffer.wrap(new byte[]{0,0,data[0],data[1]}).getInt();

		//Read uncompensated pressure
		device.write(0xF4,(byte) (0x34+(OVER_SAMPLING_RATE<<6)));
		Thread.sleep(100*OVER_SAMPLING_RATE);
		device.read(0xF6, data, 0, 3);
		int uncompensatedPressure =  buffer.wrap(new byte[]{0,data[0],data[1],data[2]}).getInt()>>(8-OVER_SAMPLING_RATE);
		
		// Calculate actual temperature using Calibration factors and uncompensated temperature
		long X1 = (long) ((unCompenstaedTemperature - AC6) * AC5 / 32768.0);
		double X2 = (MC * 2048.0) / (X1 + MD);
		double B5 = X1 + X2;
		double actualTemperature = ((B5 + 8.0) / 16.0) / 10.0; // In  Celcius
		
		// Calibration for Pressure
		double B6 = B5 - 4000;
		X1 = (long) ((B2 * (B6 * B6 / 4096.0)) / 2048.0);
		X2 = AC2 * B6 / 2048.0;
		long X3 = (long) (X1 + X2);
		double B3 = (((AC1 * 4 + X3) << OVER_SAMPLING_RATE) + 2) / 4.0;
		X1 = (long) (AC3 * B6 / 8192.0);
		X2 = (B1 * (B6 * B6 / 2048.0)) / 65536.0;
		X3 = (long) (((X1 + X2) + 2) / 4.0);
		double B4 = AC4 * (X3 + 32768) / 32768.0;
		double B7 = ((uncompensatedPressure - B3) * (50000 >> OVER_SAMPLING_RATE));
		double actualPressure = 0.0;
		if(B7 < 2147483648L)
		{
			actualPressure = (B7 * 2) / B4;
		}
		else
		{
			actualPressure = (B7 / B4) * 2;
		}
		X1 = (long) ((actualPressure / 256.0) * (actualPressure / 256.0));
		X1 = (long) ((X1 * 3038.0) / 65536.0);
		X2 = ((-7357) * actualPressure) / 65536.0;
		actualPressure = (actualPressure + (X1 + X2 + 3791) / 16.0) / 100; //In hPa
		
		// Calculate Altitude
		double altitude = 44330 * (1 - Math.pow((actualPressure / 1013.25), 0.1903)); //p0= 1013.25
		
		// Output data to screen
		System.out.println("Temperature : "+actualTemperature);
		System.out.println("Pressure : "+actualPressure);
		System.out.println("Altitude : "+altitude);
	}
}