/**
 * There must be a faster way to do this. Converts a Java byte
 * array into a Javascript ArrayBuffer, which is what the existing
 * DIS library expects as input.
 *
 * @paramter byteArray a java byte array
 * @return an ArrayBuffer with the java byte array copied into it
 */

function byteToUint8Array(byteArray) 
{
    var arrayBuffer = new ArrayBuffer(byteArray.length);
    var uint8Array = new Uint8Array(arrayBuffer);

    for(var i = 0; i < uint8Array.length; i++) 
    {
        uint8Array[i] = byteArray[i];
    }

    return arrayBuffer;

}
